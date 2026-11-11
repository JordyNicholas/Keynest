package dev.keynest.manager.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.keynest.policy.PasswordPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiProviderTest {
  private HttpServer server;
  private URI endpoint;
  private final AtomicInteger calls = new AtomicInteger();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private Handler handler;

  @FunctionalInterface
  interface Handler { void handle(HttpExchange exchange) throws Exception; }

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
    handler = exchange -> respond(exchange, 200, valid("Policy explanation."));
    server.createContext("/v1/responses", exchange -> {
      calls.incrementAndGet();
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      try { handler.handle(exchange); } catch (Exception ignored) { exchange.close(); }
    });
    server.start();
  }

  @AfterEach
  void stop() { server.stop(0); }

  private OpenAiProvider provider(Duration timeout) { return new OpenAiProvider(endpoint, timeout, "test-model"); }
  private PolicyFacts facts() { return PolicyFacts.from(PasswordPolicy.defaultPolicy()); }

  @Test
  void sendsOnlyProjectionAndExplicitTransportControlsWithoutCredentialReuse() throws Exception {
    try (var provider = provider(Duration.ofSeconds(2)); var credential = new ProviderCredential(AiCoreTest.KEY)) {
      assertThat(provider.explain(facts(), credential)).isEqualTo("Policy explanation.");
      assertThat(authorization.get()).isEqualTo("Bearer " + AiCoreTest.KEY);
      var body = AiJson.MAPPER.readTree(requestBody.get());
      assertThat(body.get("store").booleanValue()).isFalse();
      assertThat(body.get("stream").booleanValue()).isFalse();
      assertThat(body.get("max_output_tokens").intValue()).isEqualTo(512);
      assertThat(body.get("tools").isEmpty()).isTrue();
      assertThat(body.at("/text/format/strict").booleanValue()).isTrue();
      assertThat(AiJson.MAPPER.readTree(body.get("input").textValue())).isEqualTo(AiJson.MAPPER.valueToTree(facts()));
      assertThat(requestBody.get()).doesNotContain(AiCoreTest.KEY, "description", "bannedRegexes", "previous_response_id", "conversation");
      try (var next = new ProviderCredential("different-synthetic-credential")) {
        provider.explain(facts(), next);
        assertThat(authorization.get()).isEqualTo("Bearer different-synthetic-credential");
      }
      assertThat(calls.get()).isEqualTo(2);
    }
  }

  @ParameterizedTest
  @CsvSource({"401,AI_PROVIDER_AUTH_FAILED", "403,AI_PROVIDER_AUTH_FAILED", "429,AI_UNAVAILABLE", "500,AI_UNAVAILABLE", "302,AI_UNAVAILABLE"})
  void sanitizesUpstreamErrorsWithoutRetry(int status, String code) {
    handler = exchange -> respond(exchange, status, AiCoreTest.KEY);
    try (var provider = provider(Duration.ofSeconds(2)); var credential = new ProviderCredential(AiCoreTest.KEY)) {
      assertThatThrownBy(() -> provider.explain(facts(), credential)).isInstanceOf(AiFailure.class).hasMessage(code).hasNoCause();
      assertThat(calls.get()).isEqualTo(1);
    }
  }

  @Test
  void neverFollowsRedirects() {
    var redirected = new AtomicInteger();
    server.createContext("/leak", exchange -> { redirected.incrementAndGet(); exchange.close(); });
    handler = exchange -> {
      exchange.getResponseHeaders().add("Location", endpoint.resolve("/leak").toString());
      respond(exchange, 302, "");
    };
    try (var provider = provider(Duration.ofSeconds(2)); var credential = new ProviderCredential(AiCoreTest.KEY)) {
      assertThatThrownBy(() -> provider.explain(facts(), credential)).hasMessage("AI_UNAVAILABLE");
      assertThat(redirected.get()).isZero();
    }
  }

  @Test
  void doesNotInheritTheGlobalProxySelector() {
    var selected = new AtomicInteger();
    ProxySelector original = ProxySelector.getDefault();
    ProxySelector.setDefault(new ProxySelector() {
      @Override public List<Proxy> select(URI uri) { selected.incrementAndGet(); return List.of(Proxy.NO_PROXY); }
      @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) {}
    });
    try (var provider = provider(Duration.ofSeconds(2)); var credential = new ProviderCredential(AiCoreTest.KEY)) {
      provider.explain(facts(), credential);
      assertThat(selected.get()).isZero();
    } finally {
      ProxySelector.setDefault(original);
    }
  }

  @Test
  void boundsBodiesAndRejectsMalformedExtraToolAndReflectedContent() throws Exception {
    for (byte[] invalid : List.of(new byte[32769], "not-json".getBytes(StandardCharsets.UTF_8),
      valid("x".repeat(4001)), valid(AiCoreTest.KEY),
      "{\"status\":\"completed\",\"output\":[{\"type\":\"function_call\"}]}".getBytes(StandardCharsets.UTF_8),
      response("{\"explanation\":\"Text\",\"apply\":true}"),
      response("{\"explanation\":\"first\",\"explanation\":\"second\"}"))) {
      handler = exchange -> respond(exchange, 200, invalid);
      try (var provider = provider(Duration.ofSeconds(2)); var credential = new ProviderCredential(AiCoreTest.KEY)) {
        assertThatThrownBy(() -> provider.explain(facts(), credential)).hasMessage("AI_INVALID_OUTPUT").hasNoCause();
      }
    }
  }

  @Test
  void timesOutEvenWhenResponseHeadersHaveArrived() {
    var headersSent = new AtomicBoolean();
    handler = exchange -> {
      byte[] bytes = valid("Policy explanation.");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes, 0, 1);
      exchange.getResponseBody().flush();
      headersSent.set(true);
      Thread.sleep(1200);
      exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
      exchange.close();
    };
    try (var provider = provider(Duration.ofSeconds(1)); var credential = new ProviderCredential(AiCoreTest.KEY)) {
      assertThatThrownBy(() -> provider.explain(facts(), credential)).hasMessage("AI_TIMEOUT").hasNoCause();
      assertThat(headersSent.get()).isTrue();
      assertThat(calls.get()).isLessThanOrEqualTo(1);
    }
  }

  @Test
  void concurrentInvocationsDoNotMixCredentials() throws Exception {
    handler = exchange -> {
      String key = exchange.getRequestHeaders().getFirst("Authorization");
      respond(exchange, 200, valid(key.equals("Bearer first-synthetic-key") ? "first response" : "second response"));
    };
    try (var provider = provider(Duration.ofSeconds(2));
      var first = new ProviderCredential("first-synthetic-key");
      var second = new ProviderCredential("second-synthetic-key");
      var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var one = executor.submit(() -> provider.explain(facts(), first));
      var two = executor.submit(() -> provider.explain(facts(), second));
      assertThat(one.get()).isEqualTo("first response");
      assertThat(two.get()).isEqualTo("second response");
      assertThat(calls.get()).isEqualTo(2);
    }
  }

  @Test
  void rejectsUnexpectedCompression() {
    handler = exchange -> {
      exchange.getResponseHeaders().set("Content-Encoding", "gzip");
      respond(exchange, 200, valid("Policy explanation."));
    };
    try (var provider = provider(Duration.ofSeconds(2)); var credential = new ProviderCredential(AiCoreTest.KEY)) {
      assertThatThrownBy(() -> provider.explain(facts(), credential)).hasMessage("AI_INVALID_OUTPUT");
    }
  }

  @Test
  void interruptionIsPreservedAndMissingModelMakesZeroRequests() {
    try (var credential = new ProviderCredential(AiCoreTest.KEY);
      var provider = new OpenAiProvider(endpoint, Duration.ofSeconds(2), "")) {
      assertThatThrownBy(() -> provider.explain(facts(), credential)).hasMessage("AI_UNAVAILABLE");
      assertThat(calls.get()).isZero();
    }
    try (var credential = new ProviderCredential(AiCoreTest.KEY); var provider = provider(Duration.ofSeconds(2))) {
      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(() -> provider.explain(facts(), credential)).hasMessage("AI_UNAVAILABLE");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally { Thread.interrupted(); }
    }
  }

  @Test
  void testSeamCannotPointAtArbitraryDestinations() {
    assertThatThrownBy(() -> new OpenAiProvider(URI.create("http://169.254.169.254/v1/responses"), Duration.ofSeconds(2), "test"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OpenAiProvider(endpoint, Duration.ofSeconds(16), "test"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  private static byte[] valid(String explanation) throws Exception {
    return response(AiJson.MAPPER.writeValueAsString(Map.of("explanation", explanation)));
  }

  private static byte[] response(String text) throws Exception {
    return AiJson.MAPPER.writeValueAsBytes(Map.of("status", "completed", "output", List.of(Map.of(
      "type", "message", "role", "assistant", "content", List.of(Map.of("type", "output_text", "text", text))))));
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
  }

  private static void respond(HttpExchange exchange, int status, byte[] bytes) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
