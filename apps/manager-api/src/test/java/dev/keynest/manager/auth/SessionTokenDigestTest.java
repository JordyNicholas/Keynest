package dev.keynest.manager.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenDigestTest {

  @Test
  void digestIsDeterministicHexAndSeparatesInputs() {
    String digest = SessionTokenDigest.digest("opaque-session-token");

    assertThat(digest)
            .hasSize(64)
            .matches("[0-9a-f]{64}")
            .isEqualTo(SessionTokenDigest.digest("opaque-session-token"))
            .isNotEqualTo(SessionTokenDigest.digest("other-session-token"));
  }
}
