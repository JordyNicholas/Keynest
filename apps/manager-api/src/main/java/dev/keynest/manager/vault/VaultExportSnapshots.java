package dev.keynest.manager.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.policy.AccountPasswordPolicyService;
import dev.keynest.manager.vault.export.PortableVaultFormat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static dev.keynest.manager.vault.VaultExportFailure.Code.*;

/** Bounded, owner-scoped repeatable reads. No password is opened during preview. */
@Service
public class VaultExportSnapshots {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final EntityManager em;
  private final AccountPasswordPolicyService policies;
  private final VaultService vault;
  private final Clock clock;

  public VaultExportSnapshots(EntityManager em, AccountPasswordPolicyService policies, VaultService vault, Clock clock) {
    this.em = em;this.policies = policies;this.vault = vault;this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Preview preview(UserAccount owner, VaultExportSelection selection) {
    try {
      State state = state(owner, selection);
      return new Preview("keynest", true, 1, state.items().size(), state.snapshot(), clock.instant());
    } catch (VaultExportFailure expected) { throw expected; }
    catch (Exception ignored) { throw new VaultExportFailure(EXPORT_DATA_UNAVAILABLE); }
  }

  /** Caller must have just verified this user's account password; this is not an elevation token API. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ObjectNode preparePayloadForVerifiedUser(UserAccount owner, VaultExportSelection selection, String expectedSnapshot) {
    ObjectNode payload = JSON.createObjectNode();
    try {
      State state = state(owner, selection);
      if (!state.snapshot().equals(expectedSnapshot)) throw new VaultExportFailure(EXPORT_CHANGED);
      payload.put("format", "keynest.vault").put("version", 1).put("exportedAt", clock.instant().toString());
      payload.set("accountPolicy", state.policy());
      var output = payload.putArray("items");
      // The entire selection and its consistency marker have been checked before this loop.
      for (VaultItem item : state.items()) {
        ObjectNode row = output.addObject();
        put(row, "id", item.getId());put(row, "label", item.getLabel());put(row, "url", item.getUrl());
        put(row, "username", item.getUsername());row.put("password", vault.openPassword(item));
        put(row, "policyId", item.getPolicyId());put(row, "expiresAt", item.getExpiresAt());
        put(row, "lastRotatedAt", item.getLastRotatedAt());row.put("notifyBeforeDays", item.getNotifyBeforeDays());
        put(row, "createdAt", item.getCreatedAt());put(row, "updatedAt", item.getUpdatedAt());
      }
      return payload;
    } catch (VaultExportFailure expected) { payload.removeAll();throw expected; }
    catch (Exception ignored) { payload.removeAll();throw new VaultExportFailure(EXPORT_DATA_UNAVAILABLE); }
  }

  private State state(UserAccount owner, VaultExportSelection selection) throws Exception {
    var cb = em.getCriteriaBuilder();
    List<Number> headers = ciphertextLengths(owner, selection);
    if (headers.size() > PortableVaultFormat.MAX_ITEMS) throw new VaultExportFailure(EXPORT_LIMIT_EXCEEDED);
    if (selection.scope() == VaultExportSelection.Scope.SELECTED && headers.size() != selection.ids().size()) {
      throw new VaultExportFailure(EXPORT_SELECTION_UNAVAILABLE);
    }
    if (headers.isEmpty()) throw new VaultExportFailure(EXPORT_EMPTY);
    for (Number size : headers) {
      if (size == null || size.longValue() < 32 || size.longValue() > 8192) {
        throw new VaultExportFailure(EXPORT_DATA_UNAVAILABLE);
      }
    }
    // Fetch ciphertext only after checking membership, count and database-side byte lengths.
    var query = cb.createQuery(VaultItem.class);var item = query.from(VaultItem.class);
    query.select(item).where(owned(item, owner, selection));
    List<VaultItem> rows = em.createQuery(query).setMaxResults(PortableVaultFormat.MAX_ITEMS + 1).getResultList();
    if (rows.size() != headers.size()) throw new VaultExportFailure(EXPORT_CHANGED);
    rows.sort(Comparator.comparing(VaultItem::getId));
    ObjectNode policy = JSON.valueToTree(policies.inspect(owner));
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    add(digest, "keynest.export.keynest.snapshot.v1");add(digest, owner.getId());add(digest, selection.scope());
    add(digest, selection.ids().size());add(digest, rows.size());
    for (UUID selected : selection.ids()) add(digest, selected);
    byte[] policyBytes = JSON.writeValueAsBytes(policy);
    try { bytes(digest, policyBytes); } finally { Arrays.fill(policyBytes, (byte) 0); }
    for (VaultItem row : rows) {
      add(digest, row.getId());add(digest, row.getLabel());add(digest, row.getUrl());add(digest, row.getUsername());
      add(digest, row.getPolicyId());add(digest, row.getAadVersion());add(digest, row.getExpiresAt());
      add(digest, row.getLastRotatedAt());add(digest, row.getNotifyBeforeDays());add(digest, row.getCreatedAt());add(digest, row.getUpdatedAt());
      bytes(digest, row.getEnvelopeBytes());
    }
    return new State(rows, policy, HexFormat.of().formatHex(digest.digest()));
  }

  /** Native binary length avoids Hibernate's string-only octet_length descriptor.
   * Both PostgreSQL bytea and H2 varbinary support this database-side projection.
   */
  @SuppressWarnings("unchecked")
  List<Number> ciphertextLengths(UserAccount owner, VaultExportSelection selection) {
    String sql = "select octet_length(envelope_bytes) from vault_items where user_id = :owner";
    if (selection.scope() == VaultExportSelection.Scope.SELECTED) sql += " and id in (:ids)";
    var query = em.createNativeQuery(sql).setParameter("owner", owner.getId());
    if (selection.scope() == VaultExportSelection.Scope.SELECTED) query.setParameter("ids", selection.ids());
    return query.setMaxResults(PortableVaultFormat.MAX_ITEMS + 1).getResultList();
  }

  private Predicate owned(Root<VaultItem> item, UserAccount owner, VaultExportSelection selection) {
    var cb = em.getCriteriaBuilder();Predicate result = cb.equal(item.get("userId"), owner.getId());
    return selection.scope() == VaultExportSelection.Scope.ALL ? result : cb.and(result, item.get("id").in(selection.ids()));
  }

  private static void put(ObjectNode node, String field, Object value) {
    if (value == null) node.putNull(field);else node.put(field, value.toString());
  }
  private static void add(MessageDigest digest, Object value) {
    byte[] data = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
    try { bytes(digest, data); } finally { if (data != null) Arrays.fill(data, (byte) 0); }
  }
  private static void bytes(MessageDigest digest, byte[] value) {
    digest.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length).array());
    if (value != null) digest.update(value);
  }
  private record State(List<VaultItem> items, ObjectNode policy, String snapshot) {}
  public record Preview(String destination, boolean encrypted, int version, int itemCount, String snapshot, Instant asOf) {
    @Override public String toString() { return "VaultExportPreview[redacted]"; }
  }
}
