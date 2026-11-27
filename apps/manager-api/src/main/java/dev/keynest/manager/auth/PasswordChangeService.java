package dev.keynest.manager.auth;

import dev.keynest.manager.security.PasswordHashingService;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Password work precedes the parent lock; both caller-owned buffers are consumed on every exit. */
@Service
public class PasswordChangeService {
  private final AuthService auth;
  private final PasswordHashingService passwords;
  private final PasswordChangeTransactions transactions;
  private final Semaphore slot = new Semaphore(1);

  public PasswordChangeService(AuthService auth, PasswordHashingService passwords, PasswordChangeTransactions transactions) {
    this.auth = auth;this.passwords = passwords;this.transactions = transactions;
  }

  public AuthService.AuthResponse change(String authorization, char[] currentPassword, char[] newPassword) {
    boolean admitted = false;
    try {
      if (currentPassword == newPassword || currentPassword == null || currentPassword.length == 0 || currentPassword.length > 32768
        || newPassword == null || newPassword.length > 32768
        || Character.codePointCount(newPassword, 0, newPassword.length) < AuthService.MIN_MASTER_PASSWORD_LENGTH || blank(newPassword)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid request");
      }
      if (!(admitted = slot.tryAcquire())) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "busy");
      UserAccount owner = auth.requireUser(authorization);
      String verifiedHash = owner.getPasswordHash();
      if (!passwords.verifyAndClear(currentPassword, verifiedHash)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "password verification failed");
      }
      String newHash = passwords.hashAndClear(newPassword);
      String token = authorization.trim();
      if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
      return transactions.replace(owner.getId(), verifiedHash, SessionTokenDigest.digest(token), newHash);
    } finally {
      if (currentPassword != null) Arrays.fill(currentPassword, '\0');
      if (newPassword != null) Arrays.fill(newPassword, '\0');
      if (admitted) slot.release();
    }
  }

  private static boolean blank(char[] value) {
    for (int index = 0; index < value.length;) {
      int point = Character.codePointAt(value, index);
      if (!Character.isWhitespace(point)) return false;
      index += Character.charCount(point);
    }
    return true;
  }
}
