package dev.keynest.manager.vault;

/** Fixed internal outcomes, without provider/parser/SQL causes or input values. */
public final class VaultRestoreFailure extends RuntimeException {
  public enum Code { RESTORE_INVALID_FILE, RESTORE_CHANGED, RESTORE_EMPTY, RESTORE_OWNER_UNAVAILABLE, RESTORE_BUSY, RESTORE_FAILED }
  private final Code code;
  public VaultRestoreFailure(Code code) { super(code.name());this.code = code; }
  public Code code() { return code; }
}
