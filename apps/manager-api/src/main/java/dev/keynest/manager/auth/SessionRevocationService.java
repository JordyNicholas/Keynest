package dev.keynest.manager.auth;

import dev.keynest.manager.security.PasswordHashingService;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Consumes the caller-owned password; does no password work while holding the account lock. */
@Service
public class SessionRevocationService {
  private final AuthService auth;
  private final PasswordHashingService passwords;
  private final SessionRevocationTransactions transactions;
  private final Semaphore slot = new Semaphore(1);

  public SessionRevocationService(AuthService auth, PasswordHashingService passwords, SessionRevocationTransactions transactions) {
    this.auth = auth;this.passwords = passwords;this.transactions = transactions;
  }

  public void revokeAll(String authorization, char[] password) {
    boolean admitted = false;
    try {
      if (password == null || password.length == 0 || password.length > 32768) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid request");
      }
      if (!(admitted = slot.tryAcquire())) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "busy");
      UserAccount owner = auth.requireUser(authorization);
      String verifiedHash = owner.getPasswordHash();
      if (!passwords.verifyAndClear(password, verifiedHash)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "password verification failed");
      }
      // Same normalization as AuthService; requireUser has already validated this bearer.
      String token = authorization.trim();
      if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
      transactions.revoke(owner.getId(), verifiedHash, SessionTokenDigest.digest(token));
    } finally {
      if (password != null) Arrays.fill(password, '\0');
      if (admitted) slot.release();
    }
  }
}
