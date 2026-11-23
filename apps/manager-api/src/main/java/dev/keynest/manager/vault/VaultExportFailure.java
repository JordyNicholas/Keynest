package dev.keynest.manager.vault;

/** Fixed, payload-free export failures. Never retain input or provider/parser causes. */
public final class VaultExportFailure extends RuntimeException {
  public enum Code {
    EXPORT_INVALID_REQUEST, EXPORT_REQUEST_TOO_LARGE, EXPORT_SELECTION_UNAVAILABLE,
    EXPORT_LIMIT_EXCEEDED, EXPORT_EMPTY, EXPORT_REAUTH_FAILED, EXPORT_CHANGED,
    EXPORT_DATA_UNAVAILABLE, EXPORT_BUSY, EXPORT_SESSION_REQUIRED
  }
  private final Code code;
  public VaultExportFailure(Code code) { super(code.name());this.code = code; }
  public Code code() { return code; }
}
