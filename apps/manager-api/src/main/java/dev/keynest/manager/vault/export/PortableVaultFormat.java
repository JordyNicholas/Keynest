package dev.keynest.manager.vault.export;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure serialization boundary. No authentication, database, file I/O, encryption or HTTP route.
 * All inputs/outputs may contain plaintext credentials: never log them or their parser failures.
 */
public final class PortableVaultFormat {
  public static final int MAX_BYTES = 8 * 1024 * 1024;
  public static final int MAX_ITEMS = 500;
  private static final String SCHEMA = "/schemas/vault-export-v1.schema.json";
  private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
    .streamReadConstraints(StreamReadConstraints.builder()
      .maxNestingDepth(12).maxStringLength(8192).maxNumberLength(10).build())
    .build()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final JsonSchema VALIDATOR = loadSchema();

  private PortableVaultFormat() {}

  /** Reads a plaintext v1 payload, not an encrypted backup and not an import operation. */
  public static JsonNode decode(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
    try {
      JsonNode document = JSON.readTree(bytes);
      validate(document);
      return document;
    } catch (Exception ignored) {
      // Jackson/schema exceptions can contain passwords or metadata. Do not retain a cause.
      throw invalid();
    }
  }

  /** Returns UTF-8 JSON for the future encryption layer; callers must clear bytes after use. */
  public static byte[] encode(JsonNode document) {
    validate(document);
    try {
      byte[] bytes = JSON.writeValueAsBytes(document);
      if (bytes.length > MAX_BYTES) {
        java.util.Arrays.fill(bytes, (byte) 0);
        throw invalid();
      }
      return bytes;
    } catch (Exception ignored) {
      throw invalid();
    }
  }

  /**
   * Bitwarden's individual-vault JSON schema. Only login data is transferred; Keynest settings
   * and timestamps are deliberately omitted. This is a plaintext migration file, not a backup.
   */
  public static byte[] bitwardenJson(JsonNode document) {
    validate(document);
    ObjectNode result = JSON.createObjectNode();
    result.put("encrypted", false);
    result.putArray("folders");
    ArrayNode output = result.putArray("items");
    for (JsonNode source : document.get("items")) {
      ObjectNode item = output.addObject();
      item.put("type", 1);
      item.set("name", source.get("label"));
      item.put("favorite", false);
      item.put("reprompt", 0);
      ObjectNode login = item.putObject("login");
      ArrayNode uris = login.putArray("uris");
      if (!source.get("url").isNull() && !source.get("url").textValue().isBlank()) {
        ObjectNode uri = uris.addObject();
        uri.set("uri", source.get("url"));
        uri.putNull("match");
      }
      login.set("username", source.get("username"));
      login.set("password", source.get("password"));
      login.putNull("totp");
    }
    try {
      return bounded(JSON.writeValueAsBytes(result));
    } catch (Exception ignored) {
      throw invalid();
    }
  }

  /**
   * Browser login CSV candidate. Missing URLs reject the whole operation; never silently skip
   * items or invent websites. Actual destination imports must be verified before shipping.
   */
  public static byte[] browserCsv(JsonNode document) {
    validate(document);
    StringBuilder output = new StringBuilder("name,url,username,password\r\n");
    for (JsonNode item : document.get("items")) {
      if (item.get("url").isNull() || item.get("url").textValue().isBlank()) {
        throw new IllegalArgumentException("Export requires a website for every selected item");
      }
      csvCell(output, item.get("label"));
      output.append(',');
      csvCell(output, item.get("url"));
      output.append(',');
      csvCell(output, item.get("username"));
      output.append(',');
      csvCell(output, item.get("password"));
      output.append("\r\n");
    }
    return bounded(output.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] bounded(byte[] bytes) {
    if (bytes.length > MAX_BYTES) {
      java.util.Arrays.fill(bytes, (byte) 0);
      throw invalid();
    }
    return bytes;
  }

  private static void csvCell(StringBuilder output, JsonNode value) {
    // Quote fields and escape embedded quotes. Do not add apostrophes to formula-like values:
    // that would change a credential. Migration files must not be opened in spreadsheets.
    output.append('"');
    if (!value.isNull()) output.append(value.textValue().replace("\"", "\"\""));
    output.append('"');
  }

  private static void validate(JsonNode document) {
    try {
      if (document == null || !VALIDATOR.validate(document).isEmpty()) throw invalid();
      checkStringLengths(document);
      checkInstant(document.get("exportedAt"));
      JsonNode rules = document.path("accountPolicy").path("rules");
      if (rules.get("minLength").intValue() > rules.get("maxLength").intValue()) throw invalid();
      Set<UUID> ids = new HashSet<>();
      for (JsonNode item : document.get("items")) {
        String id = item.get("id").textValue();
        UUID parsed = UUID.fromString(id);
        if (!parsed.toString().equals(id) || !ids.add(parsed)) throw invalid();
        if (item.get("label").textValue().isBlank()) throw invalid();
        for (String field : new String[]{"expiresAt", "lastRotatedAt", "createdAt", "updatedAt"}) {
          if (!item.get(field).isNull()) checkInstant(item.get(field));
        }
      }
    } catch (Exception ignored) {
      throw invalid();
    }
  }

  private static void checkStringLengths(JsonNode node) {
    // Jackson's parser limit counts UTF-16 units, whereas JSON Schema counts code points.
    // Apply both limits on writes so our decoder can read every native payload we encode.
    if (node.isTextual() && node.textValue().length() > 8192) throw invalid();
    if (node.isContainerNode()) node.forEach(PortableVaultFormat::checkStringLengths);
  }

  private static void checkInstant(JsonNode node) {
    String value = node.textValue();
    if (value.length() > 40 || !value.endsWith("Z")) throw invalid();
    Instant.parse(value);
  }

  private static JsonSchema loadSchema() {
    try (InputStream input = PortableVaultFormat.class.getResourceAsStream(SCHEMA)) {
      if (input == null) throw new IllegalStateException();
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JSON.readTree(input));
    } catch (Exception ignored) {
      throw new IllegalStateException("Portable Vault schema unavailable");
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid or unsupported portable Vault payload");
  }
}
