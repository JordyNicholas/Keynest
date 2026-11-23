package dev.keynest.manager.vault;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owner-visible review metadata only, without password values or fingerprints. */
public record VaultRestoreEntry(int sourceIndex, String label, String url, String username, String matchKind, Integer repeatedOf, List<Match> matches) {
  public VaultRestoreEntry { matches = List.copyOf(matches); }
  @Override public String toString() { return "RestoreEntry[redacted]"; }
  public record Match(UUID targetId, String label, Instant createdAt, Instant updatedAt, List<String> changedFields) {
    public Match { changedFields = List.copyOf(changedFields); }
    @Override public String toString() { return "RestoreMatch[redacted]"; }
  }
}
