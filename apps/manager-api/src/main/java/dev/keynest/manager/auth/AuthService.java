package dev.keynest.manager.auth;

import dev.keynest.manager.security.PasswordHashingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

  static final int MIN_MASTER_PASSWORD_LENGTH = 15;

  private final UserAccountRepository users;
  private final AuthSessionRepository sessions;
  private final EntityManager em;
  private final PasswordHashingService passwords;
  private final String missingAccountHash;
  private final LoginAttemptLimiter loginAttempts;

  public AuthService(UserAccountRepository users, AuthSessionRepository sessions, PasswordHashingService passwords,
    LoginAttemptLimiter loginAttempts, EntityManager em) {
    this.users = users;
    this.sessions = sessions;
    this.passwords = passwords;
    this.loginAttempts = loginAttempts;
    this.em = em;
    // Non-account work target, prepared once before serving requests. Its
    // input is not a credential; a missing account can never issue a session.
    this.missingAccountHash = passwords.hash("keynest missing-account verification");
  }

  @Transactional
  public AuthResponse login(String email, String password) {
    String normalized = normalizeEmail(email);
    if (normalized.isEmpty() || normalized.length() > UserAccount.EMAIL_MAX_LENGTH || password == null
      || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
    }
    try (var attempt = loginAttempts.begin(normalized)) {
      UserAccount user = users.findByEmailIgnoreCase(normalized).orElse(null);
      // Admitted known/unknown attempts still share the same password-work budget.
      String verifiedHash = user == null ? missingAccountHash : user.getPasswordHash();
      boolean verified = passwords.verify(password, verifiedHash);
      if (user == null || !verified || !user.isActivationEligible()) {
        attempt.failed();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
      }
      // Hashing finishes before acquiring the lock shared with owner-wide revocation.
      UserAccount current = users.findForPolicyUpdate(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
      em.refresh(current, LockModeType.PESSIMISTIC_WRITE);
      if (!verifiedHash.equals(current.getPasswordHash()) || !current.isActivationEligible()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
      }
      AuthResponse response = issueSession(current);
      attempt.succeeded();
      return response;
    }
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional(readOnly = true)
  public UserAccount requireUser(String authorizationHeader) {
    String token = extractToken(authorizationHeader);
    AuthSession session = sessions.findByTokenDigest(SessionTokenDigest.digest(token))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid session"));
    if (session.isExpired(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session expired");
    }
    return users.findById(session.getUserId()).filter(UserAccount::isActivationEligible)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user missing"));
  }

  @Transactional
  public void logout(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      return;
    }
    sessions.deleteByTokenDigest(SessionTokenDigest.digest(extractToken(authorizationHeader)));
  }

  private AuthResponse issueSession(UserAccount user) {
    return AuthSessionIssuer.issue(user, sessions);
  }

  private static String extractToken(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
    }
    String value = authorizationHeader.trim();
    if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
      value = value.substring(7).trim();
    }
    if (value.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
    }
    return value;
  }

  public record AuthResponse(UUID userId, String email, String token, Instant expiresAt) {
  }
}
