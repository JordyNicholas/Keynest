package dev.keynest.manager.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.keynest.manager.vault.VaultExportFailure;
import dev.keynest.manager.vault.VaultExportSelection;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import static dev.keynest.manager.vault.VaultExportFailure.Code.*;

/** Bounded transport decoder. Credentials are owned char buffers and never record fields/logs. */
final class VaultExportRequest implements AutoCloseable {
  static final int MAX_BYTES = 96 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(32768).maxNumberLength(10).build())
    .build()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final Set<String> PREVIEW = Set.of("destination", "scope", "itemIds");
  private static final Set<String> DOWNLOAD = Set.of("destination", "scope", "itemIds", "snapshot", "accountPassword", "backupPassphrase");
  final VaultExportSelection selection;
  final String snapshot;
  final char[] accountPassword;
  final char[] backupPassphrase;

  private VaultExportRequest(VaultExportSelection selection, String snapshot, char[] accountPassword, char[] backupPassphrase) {
    this.selection = selection;this.snapshot = snapshot;this.accountPassword = accountPassword;this.backupPassphrase = backupPassphrase;
  }

  static VaultExportRequest read(HttpServletRequest request, boolean download) {
    byte[] bytes = null;
    char[] password = null;
    char[] phrase = null;
    boolean success = false;
    try {
      if (request.getQueryString() != null || request.getContentType() == null
        || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) throw invalid();
      bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new VaultExportFailure(EXPORT_REQUEST_TOO_LARGE);
      JsonNode body = JSON.readTree(bytes);
      if (body == null || !body.isObject()) throw invalid();
      var names = body.fieldNames();
      while (names.hasNext()) if (!(download ? DOWNLOAD : PREVIEW).contains(names.next())) throw invalid();
      if (!"keynest".equals(text(body, "destination"))) throw invalid();
      VaultExportSelection.Scope scope = switch (text(body, "scope")) {
        case "all" -> VaultExportSelection.Scope.ALL;
        case "selected" -> VaultExportSelection.Scope.SELECTED;
        default -> throw invalid();
      };
      var ids = new ArrayList<UUID>();
      if (body.has("itemIds")) {
        JsonNode list = body.get("itemIds");
        if (!list.isArray()) throw invalid();
        if (list.size() > 500) throw new VaultExportFailure(EXPORT_LIMIT_EXCEEDED);
        for (JsonNode value : list) {
          if (!value.isTextual()) throw invalid();
          String raw = value.textValue();UUID id = UUID.fromString(raw);
          if (!id.toString().equalsIgnoreCase(raw)) throw invalid();
          ids.add(id);
        }
      }
      var selection = new VaultExportSelection(scope, ids);
      String snapshot = null;
      if (download) {
        snapshot = text(body, "snapshot");
        if (!snapshot.matches("[0-9a-f]{64}")) throw invalid();
        String account = text(body, "accountPassword"), backup = text(body, "backupPassphrase");
        if (account.isEmpty() || account.length() > 32768 || backup.length() > 1024
          || backup.codePointCount(0, backup.length()) < 15 || backup.isBlank()) throw invalid();
        password = account.toCharArray();phrase = backup.toCharArray();
        for (int index = 0; index < phrase.length; index++) {
          if (Character.isHighSurrogate(phrase[index])) {
            if (++index >= phrase.length || !Character.isLowSurrogate(phrase[index])) throw invalid();
          } else if (Character.isLowSurrogate(phrase[index])) throw invalid();
        }
      }
      var result = new VaultExportRequest(selection, snapshot, password, phrase);
      success = true;return result;
    } catch (VaultExportFailure expected) {
      throw expected;
    } catch (Exception ignored) {
      throw invalid();
    } finally {
      if (bytes != null) Arrays.fill(bytes, (byte) 0);
      if (!success) { if (password != null) Arrays.fill(password, '\0');if (phrase != null) Arrays.fill(phrase, '\0'); }
    }
  }

  private static String text(JsonNode body, String field) {
    if (!body.path(field).isTextual()) throw invalid();
    return body.get(field).textValue();
  }
  private static VaultExportFailure invalid() { return new VaultExportFailure(EXPORT_INVALID_REQUEST); }
  @Override public void close() {
    if (accountPassword != null) Arrays.fill(accountPassword, '\0');
    if (backupPassphrase != null) Arrays.fill(backupPassphrase, '\0');
  }
  @Override public String toString() { return "VaultExportRequest[redacted]"; }
}
