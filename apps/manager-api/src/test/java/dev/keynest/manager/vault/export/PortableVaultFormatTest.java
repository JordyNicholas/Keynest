package dev.keynest.manager.vault.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PortableVaultFormatTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void nativeRoundTripPreservesSecretsMetadataNullsAndPolicyExactly() throws Exception {
    ObjectNode original = sample();
    String sentinel = "not-a-real-password,\"雪🔑\"\r\n=SUM(A1) +@-";
    item(original).put("password", sentinel);
    item(original).put("label", "Sample, \"work\"\nconta");
    item(original).putNull("username");
    item(original).putNull("url");
    JsonNode decoded = PortableVaultFormat.decode(PortableVaultFormat.encode(original));
    assertEquals(original, decoded);
    assertEquals(sentinel, decoded.path("items").get(0).path("password").textValue());
    assertTrue(decoded.path("items").get(0).path("expiresAt").isNull());
    assertEquals("keynest.historical.v1", decoded.path("items").get(0).path("policyId").textValue());
  }

  @Test
  void bitwardenJsonMapsLoginFieldsAndOmitsKeynestOnlyData() throws Exception {
    ObjectNode original = sample();
    JsonNode result = JSON.readTree(PortableVaultFormat.bitwardenJson(original));
    JsonNode target = result.path("items").get(0);
    assertFalse(result.path("encrypted").booleanValue());
    assertEquals(1, target.path("type").intValue());
    assertEquals("Synthetic item", target.path("name").textValue());
    assertEquals("https://example.invalid/login", target.path("login").path("uris").get(0).path("uri").textValue());
    assertEquals("sample", target.path("login").path("username").textValue());
    assertEquals(item(original).get("password"), target.path("login").get("password"));
    assertFalse(target.has("id"));
    assertFalse(target.has("policyId"));
    assertFalse(target.has("expiresAt"));
    assertFalse(result.has("accountPolicy"));
    item(original).putNull("url");
    item(original).putNull("username");
    result = JSON.readTree(PortableVaultFormat.bitwardenJson(original));
    assertTrue(result.path("items").get(0).path("login").path("uris").isEmpty());
    assertTrue(result.path("items").get(0).path("login").path("username").isNull());
  }

  @Test
  void csvWireFormatPreservesQuotedMultilineUnicodeAndFormulaLikeValues() throws Exception {
    ObjectNode original = sample();
    item(original).put("label", "+Synthetic, \"item\"\n雪");
    item(original).put("username", "@sample");
    item(original).put("password", "=not-a-real-password,\"quoted\"\r\n🔑");
    String csv = new String(PortableVaultFormat.browserCsv(original), StandardCharsets.UTF_8);
    // Exact independent wire expectation, including CSV quoting, CRLF and unchanged leading '='.
    assertEquals("name,url,username,password\r\n"
      + "\"+Synthetic, \"\"item\"\"\n雪\",\"https://example.invalid/login\",\"@sample\","
      + "\"=not-a-real-password,\"\"quoted\"\"\r\n🔑\"\r\n", csv);
  }

  @Test
  void csvDoesNotSilentlyDropItemsWithoutWebsites() throws Exception {
    ObjectNode original = sample();
    item(original).putNull("url");
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
      () -> PortableVaultFormat.browserCsv(original));
    assertEquals("Export requires a website for every selected item", failure.getMessage());
    assertNull(failure.getCause());
    // The richer destination can still represent this login without inventing a URL.
    assertDoesNotThrow(() -> PortableVaultFormat.bitwardenJson(original));
  }

  @Test
  void emptyVaultIsRepresentableWithoutInventedItems() throws Exception {
    ObjectNode original = sample();
    original.putArray("items");
    assertEquals(original, PortableVaultFormat.decode(PortableVaultFormat.encode(original)));
    assertEquals("name,url,username,password\r\n",
      new String(PortableVaultFormat.browserCsv(original), StandardCharsets.UTF_8));
  }

  @Test
  void rejectsUnknownVersionsFieldsSecretsAndWrongTypesWithoutEchoingData() throws Exception {
    ObjectNode original = sample();
    for (String field : new String[]{"format", "version", "exportedAt", "accountPolicy", "items"}) {
      ObjectNode invalid = original.deepCopy();
      invalid.put(field, "sensitive-sentinel");
      rejected(invalid);
    }
    for (String field : new String[]{"ownerId", "session", "masterPassword", "key", "envelope"}) {
      ObjectNode invalid = original.deepCopy();
      invalid.put(field, "sensitive-sentinel");
      rejected(invalid);
    }
    ObjectNode invalid = original.deepCopy();
    item(invalid).put("unrecognized", "sensitive-sentinel");
    rejected(invalid);
    invalid = original.deepCopy();
    invalid.put("version", 2);
    rejected(invalid);
  }

  @Test
  void rejectsMissingRequiredFieldsRatherThanApplyingDefaults() throws Exception {
    ObjectNode original = sample();
    for (String field : new String[]{"password", "label", "url", "username", "policyId", "expiresAt"}) {
      ObjectNode invalid = original.deepCopy();
      item(invalid).remove(field);
      rejected(invalid);
    }
  }

  @Test
  void rejectsDuplicateIdsInvalidTimestampsAndUnsupportedPolicyRules() throws Exception {
    ObjectNode original = sample();
    original.withArray("items").add(item(original).deepCopy());
    rejected(original);
    original = sample();
    item(original).put("createdAt", "2026-99-99T00:00:00Z");
    rejected(original);
    original = sample();
    item(original).put("id", "1-1-1-1-1");
    rejected(original);
    original = sample();
    ((ObjectNode) original.path("accountPolicy").path("rules")).put("minLength", 129);
    rejected(original);
    original = sample();
    ((ObjectNode) original.path("accountPolicy").path("rules")).putArray("bannedRegexes").add(".*");
    rejected(original);
    original = sample();
    ((ObjectNode) original.path("accountPolicy").path("rules")).put("maxLength", 8);
    rejected(original);
  }

  @Test
  void rejectsDuplicateKeysTrailingDocumentsMalformedInputAndExcessiveNesting() {
    for (String raw : new String[]{"{\"password\":\"sensitive-sentinel\",\"password\":\"duplicate\"}",
      "{} {}", "sensitive-sentinel", "[".repeat(20) + "0" + "]".repeat(20)}) {
      IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> PortableVaultFormat.decode(raw.getBytes(StandardCharsets.UTF_8)));
      assertEquals("Invalid or unsupported portable Vault payload", failure.getMessage());
      assertNull(failure.getCause());
    }
  }

  @Test
  void rejectsExcessiveItemCountAndOversizedDocuments() throws Exception {
    ObjectNode original = sample();
    ObjectNode first = item(original).deepCopy();
    for (int i = 1; i <= PortableVaultFormat.MAX_ITEMS; i++) {
      original.withArray("items").add(first.deepCopy().put("id", new java.util.UUID(0, i).toString()));
    }
    rejected(original);
    assertThrows(IllegalArgumentException.class,
      () -> PortableVaultFormat.decode(new byte[PortableVaultFormat.MAX_BYTES + 1]));
    ObjectNode longSecret = sample();
    item(longSecret).put("password", "x".repeat(8193));
    rejected(longSecret);
  }

  @Test
  void acceptsFiveHundredItemsButRejectsExcessiveEncodedBytesAndUtf16Strings() throws Exception {
    ObjectNode document = sample();
    ObjectNode first = item(document).deepCopy();
    for (int i = 2; i <= PortableVaultFormat.MAX_ITEMS; i++) {
      document.withArray("items").add(first.deepCopy().put("id", new java.util.UUID(0, i).toString()));
    }
    assertEquals(500, PortableVaultFormat.decode(PortableVaultFormat.encode(document)).path("items").size());
    for (JsonNode item : document.path("items")) ((ObjectNode) item).put("password", "雪".repeat(8192));
    assertThrows(IllegalArgumentException.class, () -> PortableVaultFormat.encode(document));
    assertThrows(IllegalArgumentException.class, () -> PortableVaultFormat.browserCsv(document));
    assertThrows(IllegalArgumentException.class, () -> PortableVaultFormat.bitwardenJson(document));
    ObjectNode wide = sample();
    item(wide).put("password", "🔑".repeat(4097));
    rejected(wide);
  }

  @Test
  void publishedSchemaMatchesPackagedContract() throws Exception {
    Path directory = Path.of("").toAbsolutePath();
    while (directory != null && !Files.isRegularFile(directory.resolve("docs/schemas/vault-export-v1.schema.json"))) {
      directory = directory.getParent();
    }
    assertNotNull(directory, "Repository schema must be available to this source test");
    try (var packaged = PortableVaultFormat.class.getResourceAsStream("/schemas/vault-export-v1.schema.json")) {
      assertNotNull(packaged);
      assertEquals(JSON.readTree(Files.readString(directory.resolve("docs/schemas/vault-export-v1.schema.json"))),
        JSON.readTree(packaged));
    }
  }

  private static void rejected(ObjectNode document) throws Exception {
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
      () -> PortableVaultFormat.decode(JSON.writeValueAsBytes(document)));
    assertEquals("Invalid or unsupported portable Vault payload", failure.getMessage());
    assertNull(failure.getCause());
    assertThrows(IllegalArgumentException.class, () -> PortableVaultFormat.encode(document));
    assertThrows(IllegalArgumentException.class, () -> PortableVaultFormat.bitwardenJson(document));
    assertThrows(IllegalArgumentException.class, () -> PortableVaultFormat.browserCsv(document));
  }

  private static ObjectNode item(ObjectNode document) {
    return (ObjectNode) document.path("items").get(0);
  }

  private static ObjectNode sample() throws Exception {
    return (ObjectNode) JSON.readTree("""
      {
        "format":"keynest.vault","version":1,"exportedAt":"2026-11-22T12:00:00Z",
        "accountPolicy":{"id":"keynest.default.v1","rules":{
          "minLength":16,"maxLength":128,"requireUpper":true,"requireLower":true,
          "requireDigit":true,"requireSymbol":true,"minCategories":3,
          "excludeAmbiguous":true,"forbidWhitespace":true,"forbidUsernameSubstring":true,
          "maxAgeDays":null,"bannedSubstrings":[],"bannedRegexes":[]
        }},
        "items":[{
          "id":"00000000-0000-0000-0000-000000000001","label":"Synthetic item",
          "url":"https://example.invalid/login","username":"sample",
          "password":"not-a-real-password","policyId":"keynest.historical.v1",
          "expiresAt":null,"lastRotatedAt":"2026-11-20T00:00:00Z","notifyBeforeDays":14,
          "createdAt":"2026-11-20T00:00:00Z","updatedAt":"2026-11-21T00:00:00Z"
        }]
      }
      """);
  }
}
