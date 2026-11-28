package dev.keynest.manager.auth.verification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Explicit one-attempt coordinator. No scheduler or HTTP route invokes it in this staged slice. */
@Service
public class VerificationDispatcher {
  private final VerificationStore store;
  private final VerificationMail mail;
  public VerificationDispatcher(VerificationStore store, VerificationMail mail) {
    this.store = store;
    this.mail = mail;
  }

  public Outcome dispatchOne() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Verification dispatch requires no account transaction");
    try {
      if (mail.readiness() != VerificationMail.Readiness.READY)
        return Outcome.UNAVAILABLE;
      var claimed = store.claimNext();
      if (claimed.isEmpty())
        return Outcome.EMPTY;
      try (var delivery = claimed.get()) {
        VerificationMail.Result result;
        try {
          result = mail.send(delivery, () -> store.canDeliver(delivery));
        } catch (RuntimeException failure) {
          result = VerificationMail.Result.AMBIGUOUS;
        }
        VerificationStore.DeliveryResult recorded = switch (result) {
          case SENT -> VerificationStore.DeliveryResult.SENT;
          case AMBIGUOUS -> VerificationStore.DeliveryResult.AMBIGUOUS;
          default -> VerificationStore.DeliveryResult.FAILED;
        };
        if (!store.finish(delivery, recorded))
          return Outcome.STALE;
        return switch (recorded) {
          case SENT -> Outcome.SENT;
          case AMBIGUOUS -> Outcome.AMBIGUOUS;
          case FAILED -> Outcome.FAILED;
        };
      }
    } catch (RuntimeException failure) {
      return Outcome.UNAVAILABLE;
    }
  }
  public enum Outcome {
    EMPTY, SENT, FAILED, AMBIGUOUS, UNAVAILABLE, STALE
  }
}
