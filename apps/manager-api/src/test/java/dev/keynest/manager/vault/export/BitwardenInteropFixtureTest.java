package dev.keynest.manager.vault.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Generates synthetic-only input for the independently checked-out upstream importer. */
class BitwardenInteropFixtureTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private record Login(String name, String url, String username, String password, List<String> uris) {}

  @Test void generateSyntheticCandidateAndIndependentExpectedLoginValues() throws Exception {
    var fixtures = JSON.createObjectNode().put("fixture", "keynest.synthetic.bitwarden.v1");
    var batches = fixtures.putArray("batches");
    var ordinary = new Login("Synthetic ordinary", "https://example.invalid/login", "synthetic-user", "not-a-real-password", List.of("https://example.invalid/login"));
    var cases = new java.util.ArrayList<>(List.of(ordinary,
      new Login("  Synthetic spaces  ", " https://example.invalid/path ", " user with spaces ", " password with spaces ", List.of(" https://example.invalid/path ")),
      new Login("Synthetic, \"雪🔑\"\r\nlabel", "https://example.invalid/?q=雪", "quoted,\"user\"\r\n雪", "=synthetic,\"雪🔑\"\r\n+@-", List.of("https://example.invalid/?q=雪")),
      new Login("Synthetic missing metadata", null, null, "1", List.of()),
      new Login("Synthetic empty metadata", "", "", "synthetic empty metadata", List.of()),
      new Login("Synthetic blank website", " \t", "synthetic", "synthetic blank website", List.of())));
    for (String prefix : List.of("=", "+", "-", "@")) cases.add(new Login("Synthetic prefix " + prefix,
      "https://example.invalid/prefix", prefix + "synthetic-user", prefix + "synthetic-password", List.of("https://example.invalid/prefix")));
    cases.add(ordinary); // Duplicate values remain separate records, with different source identities.
    addBatch(batches, cases);
    addBatch(batches, java.util.Collections.nCopies(500, ordinary));
    addBatch(batches, List.of());
    Path directory = Path.of("target", "bitwarden-interop");Files.createDirectories(directory);
    Files.write(directory.resolve("fixtures.json"), JSON.writeValueAsBytes(fixtures));
    assertEquals(3, batches.size());assertEquals(511, batches.get(0).path("expected").size() + batches.get(1).path("expected").size());
  }

  private static void addBatch(ArrayNode batches, List<Login> logins) throws Exception {
    var document = JSON.createObjectNode().put("format", "keynest.vault").put("version", 1).put("exportedAt", "2026-11-22T12:00:00Z");
    try (var policy = BitwardenInteropFixtureTest.class.getResourceAsStream("/policies/keynest.default.v1.json")) {
      assertNotNull(policy);
      document.putObject("accountPolicy").put("id", "keynest.default.v1").set("rules", JSON.readTree(policy).path("rules"));
    }
    var items = document.putArray("items");var expected = JSON.createArrayNode();
    for (int index = 0; index < logins.size(); index++) {
      Login source = logins.get(index);
      items.addObject().put("id", new UUID(0, index + 1).toString()).put("label", source.name()).put("url", source.url())
        .put("username", source.username()).put("password", source.password()).put("policyId", "keynest.synthetic.historical.v1")
        .putNull("expiresAt").put("lastRotatedAt", "2026-11-20T00:00:00Z").put("notifyBeforeDays", 14)
        .put("createdAt", "2026-11-20T00:00:00Z").put("updatedAt", "2026-11-21T00:00:00Z");
      ObjectNode login = expected.addObject().put("name", source.name()).put("username", source.username()).put("password", source.password());
      source.uris().forEach(login.putArray("uris")::add);
    }
    byte[] candidate = PortableVaultFormat.bitwardenJson(document);
    try {
      var batch = batches.addObject();batch.set("candidate", JSON.readTree(candidate));batch.set("expected", expected);
    } finally { java.util.Arrays.fill(candidate, (byte) 0);document.removeAll(); }
  }
}
