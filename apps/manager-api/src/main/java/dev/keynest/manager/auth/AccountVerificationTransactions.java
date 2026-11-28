package dev.keynest.manager.auth;

import dev.keynest.manager.auth.verification.VerificationMailbox;
import dev.keynest.manager.auth.verification.VerificationRequest;
import dev.keynest.manager.auth.verification.VerificationRequest.OwnerBinding;
import dev.keynest.manager.auth.verification.VerificationRequest.Purpose;
import dev.keynest.manager.auth.verification.VerificationStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Short account transactions only; passwords and SMTP never execute while these locks are held. */
@Service
public class AccountVerificationTransactions {
  private final UserAccountRepository users;
  private final AuthSessionRepository sessions;
  private final EntityManager em;
  private final VerificationStore store;
  private final Clock clock;

  public AccountVerificationTransactions(UserAccountRepository users, AuthSessionRepository sessions, EntityManager em,
    VerificationStore store, Clock clock) {
    this.users = users;
    this.sessions = sessions;
    this.em = em;
    this.store = store;
    this.clock = clock;
  }

  @Transactional(timeout = 10, isolation = Isolation.READ_COMMITTED)
  public void activate(char[] token, String passwordHash) {
    try {
      var request = store.consume(token, Purpose.NEW_ACCOUNT, null)
        .orElseThrow(AccountVerificationTransactions::invalidLink);
      var mailbox = VerificationMailbox.parse(request.deliveryAddress());
      if (!mailbox.identity().equals(request.recipient()))
        throw invalidLink();
      users.saveAndFlush(
        UserAccount.verified(UUID.randomUUID(), mailbox.identity(), mailbox.delivery(), passwordHash, clock.instant()));
    } catch (ResponseStatusException fixed) {
      throw fixed;
    } catch (DataIntegrityViolationException collision) {
      throw invalidLink();
    } catch (RuntimeException failure) {
      throw unavailable();
    }
  }

  @Transactional(timeout = 10, isolation = Isolation.READ_COMMITTED)
  public VerificationStore.Admission requestOwner(UUID ownerId, String verifiedHash, String digest,
    String trustedClient) {
    try {
      UserAccount owner = lock(ownerId);
      if (!owner.getPasswordHash().equals(verifiedHash))
        throw denied();
      active(ownerId, digest);
      if (owner.isEmailVerified())
        return VerificationStore.Admission.INVALID_BINDING;
      VerificationMailbox mailbox;
      try {
        mailbox = VerificationMailbox.parse(owner.getEmail());
      } catch (IllegalArgumentException invalid) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "verification unavailable for current address");
      }
      return store.admit(new VerificationRequest(Purpose.EXISTING_OWNER, mailbox.identity(), mailbox.delivery(),
        new OwnerBinding(ownerId, digest)), trustedClient);
    } catch (ResponseStatusException fixed) {
      throw fixed;
    } catch (RuntimeException failure) {
      throw unavailable();
    }
  }

  @Transactional(timeout = 10, isolation = Isolation.READ_COMMITTED)
  public void confirmOwner(UUID ownerId, String digest, char[] token) {
    try {
      UserAccount owner = lock(ownerId);
      active(ownerId, digest);
      var request = store.consume(token, Purpose.EXISTING_OWNER, new OwnerBinding(ownerId, digest))
        .orElseThrow(AccountVerificationTransactions::invalidLink);
      // Expiry is rechecked just before mutation, even if the preceding database work waited.
      active(ownerId, digest);
      owner.confirmEmail(request.deliveryAddress(), clock.instant());
      em.flush();
    } catch (ResponseStatusException fixed) {
      throw fixed;
    } catch (RuntimeException failure) {
      throw unavailable();
    }
  }

  private UserAccount lock(UUID ownerId) {
    UserAccount owner = users.findForPolicyUpdate(ownerId).orElseThrow(AccountVerificationTransactions::denied);
    em.refresh(owner, LockModeType.PESSIMISTIC_WRITE);
    return owner;
  }
  private void active(UUID ownerId, String digest) {
    AuthSession session = sessions.findByTokenDigest(digest).orElseThrow(AccountVerificationTransactions::denied);
    if (!ownerId.equals(session.getUserId()) || session.isExpired(clock.instant()))
      throw denied();
  }
  static ResponseStatusException invalidLink() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "verification link unavailable");
  }
  static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "verification unavailable");
  }
  private static ResponseStatusException denied() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session unavailable");
  }
}
