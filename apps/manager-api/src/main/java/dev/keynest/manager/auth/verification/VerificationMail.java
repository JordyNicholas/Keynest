package dev.keynest.manager.auth.verification;

import java.util.function.BooleanSupplier;

/** No provider response text, raw link, recipient or credential leaves this boundary. */
public interface VerificationMail {
  Readiness readiness();
  Result send(VerificationDelivery delivery, BooleanSupplier stillEligible);

  enum Readiness {
    UNCONFIGURED, INVALID, READY
  }
  enum Result {
    SENT, FAILED, AMBIGUOUS, UNAVAILABLE, SUPPRESSED
  }
}
