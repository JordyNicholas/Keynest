package dev.keynest.manager.ai;

import dev.keynest.manager.config.AiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public final class OpenAiProvider implements AiProvider, AutoCloseable {
  private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/responses");
  private final HttpClient client;
  private final URI endpoint;
  private final Duration timeout;
  private final String model;

  @Autowired
  public OpenAiProvider(AiProperties properties) {
    this(ENDPOINT, Duration.ofSeconds(15), properties.getOpenaiModel());
  }

  // Package-private seam for loopback mock-server tests; never exposed as configuration.
  OpenAiProvider(URI endpoint, Duration timeout, String model) {
    if (!ENDPOINT.equals(endpoint) && !("http".equals(endpoint.getScheme()) && "127.0.0.1".equals(endpoint.getHost())
      && "/v1/responses".equals(endpoint.getPath()) && endpoint.getUserInfo() == null
      && endpoint.getQuery() == null && endpoint.getFragment() == null)) {
      throw new IllegalArgumentException("unsupported AI transport destination");
    }
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(15)) > 0) {
      throw new IllegalArgumentException("AI transport timeout exceeds its bound");
    }
    this.endpoint = endpoint;
    this.timeout = timeout;
    this.model = model;
    this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER)
      .proxy(new ProxySelector() {
        @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) {}
      }).build();
  }

  @Override public String id() { return "openai"; }

  @Override
  public String explain(PolicyFacts facts, ProviderCredential credential) {
    if (model == null || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
      throw new AiFailure(AiFailure.Code.AI_UNAVAILABLE);
    }
    java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending = null;
    byte[] bytes = null;
    try {
      var schema = Map.of("type", "object", "properties", Map.of("explanation", Map.of("type", "string")),
        "required", List.of("explanation"), "additionalProperties", false);
      var body = Map.of("model", model, "store", false, "stream", false, "max_output_tokens", 512,
        "tools", List.of(), "instructions", "Explain only the supplied password-policy scalar rules in plain language. Do not request secrets, propose changes or execute actions.",
        "input", AiJson.MAPPER.writeValueAsString(facts),
        "text", Map.of("format", Map.of("type", "json_schema", "name", "keynest_policy_explanation", "strict", true, "schema", schema)));
      HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
        .header("Content-Type", "application/json").header("Accept", "application/json")
        .header("Accept-Encoding", "identity").header("Authorization", credential.authorization())
        .POST(HttpRequest.BodyPublishers.ofByteArray(AiJson.MAPPER.writeValueAsBytes(body))).build();
      pending = client.sendAsync(request, info -> new LimitedBody());
      HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      bytes = response.body();
      if (response.statusCode() == 401 || response.statusCode() == 403) throw new AiFailure(AiFailure.Code.AI_PROVIDER_AUTH_FAILED);
      if (response.statusCode() != 200) throw new AiFailure(AiFailure.Code.AI_UNAVAILABLE);
      if (!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity")) {
        throw new AiFailure(AiFailure.Code.AI_INVALID_OUTPUT);
      }
      if (credential.appearsIn(new String(bytes, StandardCharsets.UTF_8))) throw new AiFailure(AiFailure.Code.AI_INVALID_OUTPUT);
      var root = AiJson.read(bytes, AiFailure.Code.AI_INVALID_OUTPUT);
      var output = root.path("output");
      if (!"completed".equals(root.path("status").asText()) || !output.isArray() || output.size() != 1) {
        throw new AiFailure(AiFailure.Code.AI_INVALID_OUTPUT);
      }
      var message = output.get(0);
      var content = message.path("content");
      if (!"message".equals(message.path("type").asText()) || !"assistant".equals(message.path("role").asText())
        || !content.isArray() || content.size() != 1 || !"output_text".equals(content.get(0).path("type").asText())
        || !content.get(0).path("text").isTextual()) throw new AiFailure(AiFailure.Code.AI_INVALID_OUTPUT);
      var result = AiJson.read(content.get(0).get("text").textValue().getBytes(StandardCharsets.UTF_8), AiFailure.Code.AI_INVALID_OUTPUT);
      if (!result.isObject() || result.size() != 1 || !result.path("explanation").isTextual()) throw new AiFailure(AiFailure.Code.AI_INVALID_OUTPUT);
      String text = result.get("explanation").textValue();
      if (text.isBlank() || text.length() > 4000 || credential.appearsIn(text)) throw new AiFailure(AiFailure.Code.AI_INVALID_OUTPUT);
      return text;
    } catch (AiFailure failure) {
      throw failure;
    } catch (TimeoutException failure) {
      throw new AiFailure(AiFailure.Code.AI_TIMEOUT);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new AiFailure(AiFailure.Code.AI_UNAVAILABLE);
    } catch (ExecutionException failure) {
      Throwable cause = failure.getCause();
      for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
        if (cause instanceof AiFailure bounded) throw bounded;
        if (cause instanceof java.net.http.HttpTimeoutException) throw new AiFailure(AiFailure.Code.AI_TIMEOUT);
      }
      throw new AiFailure(AiFailure.Code.AI_UNAVAILABLE);
    } catch (Exception ignored) {
      throw new AiFailure(AiFailure.Code.AI_UNAVAILABLE);
    } finally {
      if (pending != null && !pending.isDone()) pending.cancel(true);
      if (bytes != null) Arrays.fill(bytes, (byte) 0);
    }
  }

  @Override public void close() { client.close(); }

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private int size;
    private boolean stopped;
    @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
    @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
    @Override public void onNext(List<ByteBuffer> buffers) {
      if (stopped) return;
      for (ByteBuffer buffer : buffers) {
        if (buffer.remaining() > 32768 - size) {
          stopped = true;
          subscription.cancel();
          delegate.onError(new AiFailure(AiFailure.Code.AI_INVALID_OUTPUT));
          return;
        }
        size += buffer.remaining();
      }
      delegate.onNext(buffers);
    }
    @Override public void onError(Throwable failure) { if (!stopped) delegate.onError(failure); }
    @Override public void onComplete() { if (!stopped) delegate.onComplete(); }
  }
}
