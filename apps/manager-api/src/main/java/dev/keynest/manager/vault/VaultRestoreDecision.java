package dev.keynest.manager.vault;

import java.util.UUID;

/** A reviewed file position. A target identity never grants authorization. */
public record VaultRestoreDecision(int sourceIndex, Action action, UUID targetId) {
  public enum Action { ADD, IGNORE, UPDATE }
  @Override public String toString() { return "RestoreDecision[redacted]"; }
}
