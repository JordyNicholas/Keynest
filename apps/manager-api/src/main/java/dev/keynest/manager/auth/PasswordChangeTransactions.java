package dev.keynest.manager.auth;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordChangeTransactions {
  private final UserAccountRepository users;
  private final AuthSessionRepository sessions;
  private final EntityManager em;

  public PasswordChangeTransactions(UserAccountRepository users, AuthSessionRepository sessions, EntityManager em) {
    this.users = users;this.sessions = sessions;this.em = em;
  }

  @Transactional(timeout = 10)
  public AuthService.AuthResponse replace(UUID owner, String verifiedHash, String actingDigest, String newHash) {
    UserAccount current = users.findForPolicyUpdate(owner).orElseThrow(PasswordChangeTransactions::denied);
    em.refresh(current, LockModeType.PESSIMISTIC_WRITE);
    if (!verifiedHash.equals(current.getPasswordHash())) throw denied();
    AuthSession acting = sessions.findByTokenDigest(actingDigest).orElseThrow(PasswordChangeTransactions::denied);
    if (!owner.equals(acting.getUserId()) || acting.isExpired(Instant.now())) throw denied();
    current.replacePasswordHash(newHash);
    sessions.deleteForOwner(owner);
    var replacement = AuthSessionIssuer.issue(current, sessions);
    em.flush();
    return replacement;
  }

  private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session unavailable"); }
}
