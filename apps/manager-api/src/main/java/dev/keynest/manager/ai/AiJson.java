package dev.keynest.manager.ai;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class AiJson {
  static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(32768).build())
    .build()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private AiJson() {}

  static JsonNode read(byte[] bytes, AiFailure.Code code) {
    try {
      JsonNode result = MAPPER.readTree(bytes);
      if (result == null) throw new AiFailure(code);
      return result;
    } catch (Exception ignored) {
      throw new AiFailure(code);
    }
  }
}
