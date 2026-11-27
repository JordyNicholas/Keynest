package dev.keynest.manager.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.keynest.manager.api.ApiPaths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
  "keynest.rate-limit.enabled=true", "keynest.rate-limit.limit=1",
  "keynest.rate-limit.trusted-proxies=127.0.0.1/32",
  "keynest.login-throttle.failure-limit=2", "keynest.login-throttle.cooldown-seconds=30"
})
class LoginThrottleHttpIntegrationTest {
  @LocalServerPort int port;
  @Autowired AuthService auth;
  @Autowired AuthSessionRepository sessions;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private static final String PASSWORD = "Synthetic account passphrase!42";
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void changingResolvedClientsCannotResetAKnownOrUnknownIdentifierBudget() throws Exception {
    String known = "throttle-" + UUID.randomUUID() + "@example.invalid";
    String absent = "missing-" + UUID.randomUUID() + "@example.invalid";
    auth.register(known, PASSWORD);
    long existingSessions = sessions.count();
    for (int i = 0; i < 2; i++) {
      assertThat(login(" " + known.toUpperCase(java.util.Locale.ROOT) + " ", "wrong synthetic password", "198.51.100." + (10 + i)).statusCode()).isEqualTo(401);
      assertThat(login(absent, "wrong synthetic password", "198.51.100." + (20 + i)).statusCode()).isEqualTo(401);
    }
    for (String identifier : new String[] {known, absent}) {
      var limited = login(identifier, PASSWORD, identifier.equals(known) ? "198.51.100.30" : "198.51.100.31");
      assertThat(limited.statusCode()).isEqualTo(429);
      assertThat(Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow())).isBetween(1L, 30L);
      assertThat(limited.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store");
      assertThat(limited.body().contains(identifier) || limited.body().contains(PASSWORD)).isFalse();
    }
    assertThat(sessions.count()).isEqualTo(existingSessions);
    String independent = "independent-" + UUID.randomUUID() + "@example.invalid";
    auth.register(independent, PASSWORD);
    assertThat(login(independent, PASSWORD, "198.51.100.40").statusCode()).isEqualTo(200);
  }

  @Test
  void successfulSignInClearsFailureHistoryAndTheClientLimiterStillApplies() throws Exception {
    String email = "success-" + UUID.randomUUID() + "@example.invalid";auth.register(email, PASSWORD);
    assertThat(login(email, "wrong synthetic password", "198.51.100.50").statusCode()).isEqualTo(401);
    assertThat(login(email, PASSWORD, "198.51.100.51").statusCode()).isEqualTo(200);
    assertThat(login(email, "wrong synthetic password", "198.51.100.52").statusCode()).isEqualTo(401);
    assertThat(login(email, PASSWORD, "198.51.100.53").statusCode()).isEqualTo(200);
    // The same resolved client remains limited independently of account success.
    assertThat(login(email, PASSWORD, "198.51.100.53").statusCode()).isEqualTo(429);
  }

  private HttpResponse<String> login(String email, String password, String forwarded) throws Exception {
    String body = JSON.createObjectNode().put("email", email).put("password", password).toString();
    return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ApiPaths.AUTH_LOGIN))
      .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
      .header("X-Forwarded-For", forwarded).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
}
