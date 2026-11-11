package dev.keynest.manager.ai;

/** Public errors contain only these fixed codes, never provider/credential details. */
public final class AiFailure extends RuntimeException {
  public enum Code {
    AI_DISABLED(503), AI_INVALID_REQUEST(400), AI_REQUEST_TOO_LARGE(413),
    AI_PROVIDER_UNSUPPORTED(400), AI_POLICY_UNSUPPORTED(400), AI_CREDENTIAL_REQUIRED(400),
    AI_CREDENTIAL_INVALID(400), AI_RATE_LIMITED(429), AI_CAPACITY_LIMITED(429),
    AI_PROVIDER_AUTH_FAILED(502), AI_TIMEOUT(504), AI_UNAVAILABLE(502), AI_INVALID_OUTPUT(502);

    private final int status;
    Code(int status) { this.status = status; }
    public int status() { return status; }
  }

  private final Code code;
  public AiFailure(Code code) {
    super(code.name(), null, false, false);
    this.code = code;
  }
  public Code code() { return code; }
}
