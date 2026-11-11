package dev.keynest.manager.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** One-way database lookup value for a high-entropy opaque session token. */
final class SessionTokenDigest {

  private SessionTokenDigest() {
  }

  static String digest(String token) {
    Objects.requireNonNull(token, "token");
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
              .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
