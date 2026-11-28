package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.keynest.manager.testing.TestAccounts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationRetirementHttpIntegrationTest {
  @Value("${local.server.port}")
  int port;
  @Autowired
  TestAccounts accounts;
  @Autowired
  UserAccountRepository users;
  @Autowired
  AuthSessionRepository sessions;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Test
  void refusesKnownUnknownMalformedAndOldClientBodiesWithoutCreatingAuthority() throws Exception {
    var existing = accounts.register("retired-signup@example.invalid", "Existing synthetic account proof!42");
    long userCount = users.count(), sessionCount = sessions.count();
    for (String body : List.of("{}", "not-json",
      "{\"email\":\"retired-signup@example.invalid\",\"password\":\"ignored\"}",
      "{\"email\":\"missing@example.invalid\",\"password\":\"ignored\"}", " ".repeat(128 * 1024))) {
      var reply = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register"))
        .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(reply.statusCode()).isEqualTo(410);
      assertThat(reply.body()).isEqualTo("{\"code\":\"REGISTRATION_REQUIRES_VERIFICATION\"}");
      assertThat(reply.headers().firstValue("Cache-Control")).contains("no-store");
    }
    assertThat(users.count()).isEqualTo(userCount);
    assertThat(sessions.count()).isEqualTo(sessionCount);
    assertThat(existing.token()).isNotBlank();
  }
}
