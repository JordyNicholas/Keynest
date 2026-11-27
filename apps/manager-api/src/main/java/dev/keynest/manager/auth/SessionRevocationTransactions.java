package dev.keynest.manager.auth;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Fresh transaction boundary after password work; shares the parent lock with sign-in issuance. */
@Service
public class SessionRevocationTransactions {
  private final UserAccountRepository users;
  private final AuthSessionRepository sessions;
  private final EntityManager em;

  public SessionRevocationTransactions(UserAccountRepository users, AuthSessionRepository sessions, EntityManager em) {
    this.users = users;this.sessions = sessions;this.em = em;
  }

  @Transactional(timeout = 10)
  public void revoke(UUID owner, String verifiedHash, String actingDigest) {
    UserAccount current = users.findForPolicyUpdate(owner).orElseThrow(SessionRevocationTransactions::denied);
    em.refresh(current, LockModeType.PESSIMISTIC_WRITE);
    if (!verifiedHash.equals(current.getPasswordHash())) throw denied();
    AuthSession acting = sessions.findByTokenDigest(actingDigest).orElseThrow(SessionRevocationTransactions::denied);
    if (!owner.equals(acting.getUserId()) || acting.isExpired(Instant.now())) throw denied();
    sessions.deleteForOwner(owner);
    em.flush();
  }

  private static ResponseStatusException denied() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session unavailable");
  }
}
