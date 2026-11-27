package dev.keynest.manager.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

/** Strict single-field transport; no credentials in generated record/toString output. */
final class SessionRevocationRequest implements AutoCloseable {
  static final int MAX_BYTES = 200 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).maxStringLength(32768).maxNumberLength(10).build())
    .build()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  final char[] password;

  private SessionRevocationRequest(char[] password) { this.password = password; }

  static SessionRevocationRequest read(HttpServletRequest request) {
    byte[] bytes = null;
    try {
      if (request.getQueryString() != null || request.getContentType() == null
        || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) throw invalid();
      bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
      var body = JSON.readTree(bytes);
      if (body == null || !body.isObject() || body.size() != 1 || !body.path("accountPassword").isTextual()) throw invalid();
      String password = body.path("accountPassword").textValue();
      if (password.isEmpty() || password.length() > 32768) throw invalid();
      return new SessionRevocationRequest(password.toCharArray());
    } catch (ResponseStatusException failure) {
      throw failure;
    } catch (Exception ignored) {
      throw invalid();
    } finally {
      if (bytes != null) Arrays.fill(bytes, (byte) 0);
    }
  }

  private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST); }
  @Override public void close() { Arrays.fill(password, '\0'); }
  @Override public String toString() { return "SessionRevocationRequest[redacted]"; }
}
