package dev.keynest.manager.vault;

import java.time.Instant;
import java.util.UUID;

/** Read projection: deliberately excludes ciphertext, owner identity and all secrets. */
public record VaultMetadata(
  UUID id, String label, String url, String username, String policyId,
  Instant expiresAt, Instant lastRotatedAt, Integer notifyBeforeDays,
  Instant createdAt, Instant updatedAt
) {
  public record Summary(UUID id, String label, String url, Instant expiresAt, Instant createdAt, Instant updatedAt) {}
  public record Counts(long total, long expired, long soon, long upcoming, long later, long unset) {
    public long needsAttention() { return expired + soon; }
  }
}
