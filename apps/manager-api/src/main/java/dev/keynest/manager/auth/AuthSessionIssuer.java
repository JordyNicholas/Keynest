package dev.keynest.manager.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/** The existing 256-bit opaque token and seven-day lifetime, shared by account flows. */
final class AuthSessionIssuer {
  private static final SecureRandom RANDOM = new SecureRandom();
  private AuthSessionIssuer() {}

  static AuthService.AuthResponse issue(UserAccount user, AuthSessionRepository sessions) {
    byte[] raw = new byte[32];
    String token;
    try {
      RANDOM.nextBytes(raw);
      token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    } finally { Arrays.fill(raw, (byte) 0); }
    Instant now = Instant.now();
    var session = new AuthSession(UUID.randomUUID(), user.getId(), SessionTokenDigest.digest(token),
      now.plus(7, ChronoUnit.DAYS), now);
    sessions.save(session);
    return new AuthService.AuthResponse(user.getId(), user.getEmail(), token, session.getExpiresAt());
  }
}
