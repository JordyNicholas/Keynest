package dev.keynest.manager.vault;

import dev.keynest.manager.auth.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Component;
import static dev.keynest.manager.vault.VaultRestoreFailure.Code.*;

/** Bounded process-local single-use plans; no file, plaintext credential or raw session is stored. */
@Component
public class VaultRestorePlans {
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Plan> plans = new HashMap<>();
  static final int MAX_PLANS = 64;
  public VaultRestorePlans(Clock clock) { this.clock = clock; }

  public synchronized Ticket issue(Binding binding, VaultRestoreService.Preview preview) {
    purge();if (plans.size() >= MAX_PLANS) throw new VaultRestoreFailure(RESTORE_BUSY);
    byte[] bytes = new byte[32];String token;
    do { random.nextBytes(bytes);token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); } while (plans.containsKey(hash(token)));
    Arrays.fill(bytes, (byte) 0);
    Instant expires = clock.instant().plusSeconds(300);
    plans.put(hash(token), new Plan(binding, preview.snapshot(), preview.itemCount(), expires, preview.decisions()));
    return new Ticket(token, expires, preview.itemCount(), preview.newItemCount(), preview.updatedItemCount(), preview.skippedCount(), preview.entries(), true);
  }

  public synchronized Plan consume(String token, Binding binding) {
    purge();
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new VaultRestoreFailure(RESTORE_PLAN_INVALID);
    String key = hash(token);Plan plan = plans.get(key);
    if (plan == null || !plan.binding().equals(binding)) throw new VaultRestoreFailure(RESTORE_PLAN_INVALID);
    plans.remove(key);return plan;
  }

  private void purge() { Instant now = clock.instant();plans.values().removeIf(plan -> !plan.expiresAt().isAfter(now)); }
  public static Binding binding(String authorization, UserAccount owner) {
    // Match AuthService's existing normalization; this does not change session transport.
    String token = authorization.trim();if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
    return new Binding(owner.getId(), hash("restore.session\0" + token), hash("restore.credential\0" + owner.getPasswordHash()));
  }
  private static String hash(String input) {
    byte[] value = input.getBytes(StandardCharsets.UTF_8);
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    catch (Exception ignored) { throw new VaultRestoreFailure(RESTORE_FAILED); }
    finally { Arrays.fill(value, (byte) 0); }
  }
  public record Binding(UUID owner, String session, String credential) { @Override public String toString() { return "RestoreBinding[redacted]"; } }
  public record Plan(Binding binding, String snapshot, int itemCount, Instant expiresAt, List<VaultRestoreDecision> decisions) {
    public Plan { decisions = List.copyOf(decisions); }
    @Override public String toString() { return "RestorePlan[redacted]"; }
  }
  public record Ticket(String planId, Instant expiresAt, int itemCount, int newItemCount, int updatedItemCount, int skippedCount, List<VaultRestoreEntry> entries, boolean receivingPolicyUnchanged) { @Override public String toString() { return "RestoreTicket[redacted]"; } }
}
