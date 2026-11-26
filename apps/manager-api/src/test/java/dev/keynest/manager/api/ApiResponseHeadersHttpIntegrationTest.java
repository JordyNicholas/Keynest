package dev.keynest.manager.api;

import dev.keynest.manager.auth.AuthService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
  "keynest.rate-limit.limit=1", "keynest.tools.demo.enabled=false", "keynest.ai.enabled=false"
})
@Import(ApiResponseHeadersHttpIntegrationTest.Fixtures.class)
class ApiResponseHeadersHttpIntegrationTest {
  @Value("${local.server.port}") int port;
  @Autowired AuthService auth;
  private final HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

  private HttpResponse<String> request(String method, String path, String body, String... headers) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
      .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
    if (headers.length > 0) builder.headers(headers);
    var response = client.send(builder.method(method, body == null
      ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
      HttpResponse.BodyHandlers.ofString());
    assertThat(response.headers().allValues("X-Content-Type-Options")).containsExactly("nosniff");
    assertThat(response.headers().allValues("Referrer-Policy")).containsExactly("no-referrer");
    assertThat(response.headers().allValues("X-Frame-Options")).containsExactly("DENY");
    assertThat(response.headers().allValues("Content-Security-Policy"))
      .containsExactly("default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
    assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    return response;
  }

  @Test
  void realHttpSuccessAndRejectionsHaveTheSameBaseline() throws Exception {
    assertThat(request("GET", ApiPaths.TOOLS_HEALTH, null).statusCode()).isEqualTo(200);
    assertThat(request("POST", ApiPaths.AUTH_REGISTER, "{}").statusCode()).isEqualTo(400);
    assertThat(request("GET", ApiPaths.VAULT_ITEMS, null).statusCode()).isEqualTo(401);
    assertThat(request("GET", "/api/v1/not-a-route", null).statusCode()).isEqualTo(404);
    assertThat(request("POST", ApiPaths.TOOLS_SEAL, "{}").statusCode()).isEqualTo(403);
    String token = auth.register("headers-" + UUID.randomUUID() + "@example.invalid",
      "Synthetic account password!42").token();
    assertThat(request("POST", ApiPaths.AI_EXPLAIN,
      "{\"providerId\":\"openai\",\"policyId\":\"keynest.default.v1\"}",
      "Authorization", "Bearer " + token).statusCode()).isEqualTo(503);
  }

  @Test
  void corsPreflightGrantsOnlyTheConfiguredOrigin() throws Exception {
    var allowed = request("OPTIONS", ApiPaths.VAULT_ITEMS, null,
      "Origin", "http://localhost:4200", "Access-Control-Request-Method", "POST",
      "Access-Control-Request-Headers", "authorization,content-type");
    assertThat(allowed.statusCode()).isEqualTo(200);
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:4200");
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    var denied = request("OPTIONS", ApiPaths.VAULT_ITEMS, null,
      "Origin", "https://unlisted.example.invalid", "Access-Control-Request-Method", "POST");
    assertThat(denied.statusCode()).isEqualTo(403);
    assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }

  @Test
  void boundsAndRateRejectionsPreserveNoStoreAndRetryAfter() throws Exception {
    for (String path : new String[]{ApiPaths.VAULT_EXPORT_PREVIEW, ApiPaths.VAULT_RESTORE_PREVIEW}) {
      var first = request("POST", path, "{}");
      assertThat(first.statusCode()).isEqualTo(401);
      assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
      var limited = request("POST", path, "{}");
      assertThat(limited.statusCode()).isEqualTo(429);
      assertThat(limited.headers().firstValue("Cache-Control")).contains("no-store");
      assertThat(limited.headers().firstValue("Retry-After")).contains("60");
    }
    var oversized = request("POST", ApiPaths.VAULT_EXPORT_PREVIEW, " ".repeat(VaultExportRequest.MAX_BYTES + 1));
    assertThat(oversized.statusCode()).isEqualTo(413);
    assertThat(oversized.headers().firstValue("Cache-Control")).contains("no-store");
  }

  @Test
  void servletErrorResetAndAsyncDispatchesKeepHeaders() throws Exception {
    assertThat(request("GET", "/_header-fixture/error", null).statusCode()).isEqualTo(500);
    var reset = request("GET", "/_header-fixture/reset", null);
    assertThat(reset.statusCode()).isEqualTo(200);
    assertThat(reset.body()).isEqualTo("{}");
    assertThat(request("GET", "/_header-fixture/async", null).statusCode()).isEqualTo(200);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fixtures {
    @Bean
    FilterRegistrationBean<Filter> headerDispatchFixtures() {
      Filter fixture = (request, response, chain) -> {
        String path = ((HttpServletRequest) request).getRequestURI();
        var http = (HttpServletResponse) response;
        switch (path) {
          case "/_header-fixture/error" -> http.sendError(500);
          case "/_header-fixture/reset" -> {
            http.reset();
            http.setContentType("application/json");
            http.getWriter().write("{}");
            http.flushBuffer();
          }
          case "/_header-fixture/async" -> request.startAsync().dispatch(ApiPaths.TOOLS_HEALTH);
          default -> chain.doFilter(request, response);
        }
      };
      var registration = new FilterRegistrationBean<>(fixture);
      registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
      registration.setDispatcherTypes(DispatcherType.REQUEST);
      registration.setAsyncSupported(true);
      return registration;
    }
  }
}
