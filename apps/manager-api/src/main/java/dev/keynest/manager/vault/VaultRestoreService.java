package dev.keynest.manager.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.manager.auth.UserAccountRepository;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static dev.keynest.manager.vault.VaultRestoreFailure.Code.*;

/**
 * Restore core. The trusted overload is internal; HTTP supplies a transactional authorization guard.
 * No agent capability is registered.
 */
@Service
public class VaultRestoreService {
  private final UserAccountRepository users;
  private final EntityManager em;
  private final VaultService vault;
  private final EncryptedVaultBackup backups;
  private final VaultExportAdmission admission;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final VaultRestoreComparison comparison;

  public VaultRestoreService(UserAccountRepository users, EntityManager em, VaultService vault,
      EncryptedVaultBackup backups, VaultExportAdmission admission, PlatformTransactionManager transactions, Clock clock, VaultRestoreComparison comparison) {
    this.users = users;this.em = em;this.vault = vault;this.backups = backups;this.admission = admission;this.clock = clock;
    transaction = new TransactionTemplate(transactions);
    // Commit/rollback completes inside the work lease, independent of any caller transaction.
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setTimeout(30);this.comparison = comparison;
  }

  public Preview preview(UUID receiver, byte[] file, char[] passphrase) {
    return preview(receiver, file, passphrase, null);
  }

  public Preview preview(UUID receiver, byte[] file, char[] passphrase, List<VaultRestoreDecision> decisions) {
    try (var work = acquire();var decoded = open(receiver, file, passphrase, null)) {
      return transaction.execute(status -> {
        lock(receiver);
        try {
          var state = comparison.inspect(receiver, decoded.payload.path("items"));
          var selection = state.select(decisions);
          return new Preview(decoded.payload.path("items").size(), selection.newItemCount(), selection.updatedItemCount(), selection.skippedCount(), state.entries(), selection.decisions(), snapshot(receiver, decoded.file) + "." + state.snapshot(), clock.instant(), true);
        } catch (VaultRestoreFailure expected) { throw expected; }
        catch (Exception ignored) { throw new VaultRestoreFailure(RESTORE_FAILED); }
      });
    } catch (VaultRestoreFailure expected) { throw expected; }
    catch (Exception ignored) { throw new VaultRestoreFailure(RESTORE_FAILED); }
    finally { wipe(passphrase); }
  }

  /** The consistency marker binds the file and receiving state; it is not an approval token. */
  public Result restore(UUID receiver, byte[] file, char[] passphrase, String reviewedSnapshot) {
    return restore(receiver, file, passphrase, reviewedSnapshot, () -> {});
  }

  /** Runs the transport guard inside the receiving transaction before writing and before commit. */
  public Result restore(UUID receiver, byte[] file, char[] passphrase, String reviewedSnapshot, Runnable authorizationCheck) {
    return restore(receiver, file, passphrase, reviewedSnapshot, null, authorizationCheck);
  }

  public Result restore(UUID receiver, byte[] file, char[] passphrase, String reviewedSnapshot, List<VaultRestoreDecision> decisions, Runnable authorizationCheck) {
    try (var work = acquire()) {
      if (reviewedSnapshot == null || !reviewedSnapshot.matches("[0-9a-f]{64}\\.[0-9a-f]{64}")) throw new VaultRestoreFailure(RESTORE_CHANGED);
      String[] reviewed = reviewedSnapshot.split("\\.");
      try (var decoded = open(receiver, file, passphrase, reviewed[0])) {
        return transaction.execute(status -> {
          lock(receiver);authorizationCheck.run();
          VaultRestoreComparison.State state;
          try { state = comparison.inspect(receiver, decoded.payload.path("items")); }
          catch (VaultRestoreFailure expected) { throw expected; }
          catch (Exception ignored) { throw new VaultRestoreFailure(RESTORE_FAILED); }
          if (!state.snapshot().equals(reviewed[1])) throw new VaultRestoreFailure(RESTORE_CHANGED);
          var selection = state.select(decisions);
          for (var operation : selection.operations()) {
            JsonNode row = operation.row();UUID id = operation.targetId() == null ? UUID.randomUUID() : operation.targetId();String policyId = row.path("policyId").textValue();
            // Preserve historical passwords rather than applying today's receiving policy.
            byte[] envelope = vault.sealPassword(row.path("password").textValue(), receiver, id, policyId);
            if (envelope.length > 8192) throw new VaultRestoreFailure(RESTORE_LIMIT_EXCEEDED);
            if (operation.targetId() == null) em.persist(new VaultItem(id, receiver, text(row, "label"), text(row, "url"), text(row, "username"),
              envelope, policyId, VaultItemAad.VERSION_ITEM_V1, instant(row, "expiresAt"), instant(row, "lastRotatedAt"),
              row.path("notifyBeforeDays").isNull() ? null : row.path("notifyBeforeDays").intValue(),
              instant(row, "createdAt"), instant(row, "updatedAt")));
            else {
              VaultItem target = em.find(VaultItem.class, id);
              if (target == null || !receiver.equals(target.getUserId())) throw new VaultRestoreFailure(RESTORE_CHANGED);
              target.setLabel(text(row, "label"));target.setUrl(text(row, "url"));target.setUsername(text(row, "username"));
              target.setEnvelopeBytes(envelope);target.setPolicyId(policyId);target.setAadVersion(VaultItemAad.VERSION_ITEM_V1);
              target.setExpiresAt(instant(row, "expiresAt"));target.setLastRotatedAt(instant(row, "lastRotatedAt"));
              target.setNotifyBeforeDays(row.path("notifyBeforeDays").isNull() ? null : row.path("notifyBeforeDays").intValue());target.setUpdatedAt(clock.instant());
            }
          }
          // persist never merges an existing identity, including an unlikely UUID collision.
          em.flush();authorizationCheck.run();
          return new Result(selection.newItemCount(), selection.updatedItemCount(), selection.skippedCount(), true);
        });
      }
    } catch (VaultRestoreFailure expected) { throw expected; }
    catch (Exception ignored) { throw new VaultRestoreFailure(RESTORE_FAILED); }
    finally { wipe(passphrase); }
  }

  private VaultExportAdmission.Lease acquire() {
    try { return admission.acquire(); }
    catch (VaultExportFailure ignored) { throw new VaultRestoreFailure(RESTORE_BUSY); }
  }

  private void lock(UUID receiver) {
    var owner = users.findForPolicyUpdate(receiver).orElseThrow(() -> new VaultRestoreFailure(RESTORE_OWNER_UNAVAILABLE));em.refresh(owner);
  }

  private Decoded open(UUID receiver, byte[] file, char[] passphrase, String expected) {
    if (receiver == null || !users.existsById(receiver)) throw new VaultRestoreFailure(RESTORE_OWNER_UNAVAILABLE);
    if (file == null || file.length == 0 || file.length > EncryptedVaultBackup.MAX_FILE_BYTES) throw new VaultRestoreFailure(RESTORE_INVALID_FILE);
    byte[] copy = file.clone();ObjectNode payload = null;
    try {
      if (expected != null && !snapshot(receiver, copy).equals(expected)) throw new VaultRestoreFailure(RESTORE_CHANGED);
      try { payload = (ObjectNode) backups.decrypt(copy, passphrase); }
      catch (IllegalStateException ignored) { throw new VaultRestoreFailure(RESTORE_BUSY); }
      catch (Exception ignored) { throw new VaultRestoreFailure(RESTORE_INVALID_FILE); }
      if (payload.path("items").isEmpty()) throw new VaultRestoreFailure(RESTORE_EMPTY);
      return new Decoded(copy, payload);
    } catch (Exception failure) {
      Arrays.fill(copy, (byte) 0);if (payload != null) payload.removeAll();throw failure;
    }
  }

  private static String snapshot(UUID receiver, byte[] file) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update("keynest.restore.file.v1\0".getBytes(StandardCharsets.US_ASCII));
      digest.update(ByteBuffer.allocate(16).putLong(receiver.getMostSignificantBits()).putLong(receiver.getLeastSignificantBits()).array());
      digest.update(file);return HexFormat.of().formatHex(digest.digest());
    } catch (Exception ignored) { throw new VaultRestoreFailure(RESTORE_FAILED); }
  }
  private static String text(JsonNode row, String field) { return row.path(field).isNull() ? null : row.path(field).textValue(); }
  private static Instant instant(JsonNode row, String field) { String value = text(row, field);return value == null ? null : Instant.parse(value); }
  private static void wipe(char[] value) { if (value != null) Arrays.fill(value, '\0'); }

  private static final class Decoded implements AutoCloseable {
    private final byte[] file;
    private final ObjectNode payload;
    private Decoded(byte[] file, ObjectNode payload) { this.file = file;this.payload = payload; }
    @Override public void close() { Arrays.fill(file, (byte) 0);payload.removeAll(); }
    @Override public String toString() { return "DecodedRestore[redacted]"; }
  }
  public record Preview(int itemCount, int newItemCount, int updatedItemCount, int skippedCount, List<VaultRestoreEntry> entries, List<VaultRestoreDecision> decisions, String snapshot, Instant asOf, boolean receivingPolicyUnchanged) {
    public Preview { entries = List.copyOf(entries);decisions = List.copyOf(decisions); }
    public Preview(int itemCount, String snapshot, Instant asOf, boolean receivingPolicyUnchanged) { this(itemCount, itemCount, 0, 0, List.of(), List.of(), snapshot, asOf, receivingPolicyUnchanged); }
    @Override public String toString() { return "VaultRestorePreview[redacted]"; }
  }
  public record Result(int restoredCount, int updatedCount, int skippedCount, boolean receivingPolicyUnchanged) {}
}
