package dev.keynest.manager.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import dev.keynest.manager.vault.VaultRestoreDecision;
import dev.keynest.manager.vault.VaultRestoreFailure;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.http.MediaType;
import static dev.keynest.manager.vault.VaultRestoreFailure.Code.*;

/** Bounded streaming fields/decision count: nested untrusted arrays never become a JSON tree. */
final class VaultRestoreRequest implements AutoCloseable {
  private static final int MAX_BASE64 = ((EncryptedVaultBackup.MAX_FILE_BYTES + 2) / 3) * 4;
  static final int MAX_BYTES = MAX_BASE64 + 256 * 1024;
  private static final Semaphore SLOT = new Semaphore(1);
  private static final JsonFactory JSON = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
    .maxNestingDepth(3).maxStringLength(MAX_BASE64).maxNumberLength(10).build()).build().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
  private static final Set<String> PREVIEW = Set.of("file", "backupPassphrase", "decisions");
  private static final Set<String> APPLY = Set.of("file", "backupPassphrase", "accountPassword", "planId", "confirmUpdates");
  final byte[] file;
  final char[] passphrase;
  final char[] accountPassword;
  final String planId;
  final List<VaultRestoreDecision> decisions;
  final boolean confirmUpdates;
  private boolean closed;
  private VaultRestoreRequest(byte[] file, char[] phrase, char[] account, String plan, List<VaultRestoreDecision> decisions, boolean confirmUpdates) {
    this.file = file;this.passphrase = phrase;this.accountPassword = account;this.planId = plan;this.decisions = decisions;this.confirmUpdates = confirmUpdates;
  }

  static VaultRestoreRequest read(HttpServletRequest request, boolean apply) {
    if (!SLOT.tryAcquire()) throw new VaultRestoreFailure(RESTORE_BUSY);
    byte[] bytes = null, file = null;char[] phrase = null, account = null;boolean success = false;
    try {
      if (request.getQueryString() != null || request.getContentType() == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) throw invalid();
      bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new VaultRestoreFailure(RESTORE_REQUEST_TOO_LARGE);
      var fields = new HashMap<String, String>();List<VaultRestoreDecision> decisions = null;boolean confirmUpdates = false;
      try (var parser = JSON.createParser(bytes)) {
        if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalid();String name = parser.currentName();
          if (!(apply ? APPLY : PREVIEW).contains(name)) throw invalid();
          parser.nextToken();
          if (name.equals("decisions")) decisions = decisions(parser);
          else if (name.equals("confirmUpdates")) {
            if (parser.currentToken() != JsonToken.VALUE_TRUE && parser.currentToken() != JsonToken.VALUE_FALSE) throw invalid();confirmUpdates = parser.getBooleanValue();
          } else fields.put(name, string(parser));
        }
        if (parser.nextToken() != null) throw invalid();
      }
      String encoded = fields.get("file");if (encoded == null || encoded.isEmpty()) throw invalid();if (encoded.length() > MAX_BASE64) throw new VaultRestoreFailure(RESTORE_REQUEST_TOO_LARGE);
      file = Base64.getDecoder().decode(encoded);
      if (file.length == 0 || file.length > EncryptedVaultBackup.MAX_FILE_BYTES) throw new VaultRestoreFailure(RESTORE_REQUEST_TOO_LARGE);
      if (!Base64.getEncoder().encodeToString(file).equals(encoded)) throw invalid();
      String backup = fields.get("backupPassphrase");
      if (backup == null || backup.length() > 1024 || backup.codePointCount(0, backup.length()) < 15 || backup.isBlank()) throw invalid();
      phrase = backup.toCharArray();
      for (int index = 0; index < phrase.length; index++) {
        if (Character.isHighSurrogate(phrase[index])) { if (++index >= phrase.length || !Character.isLowSurrogate(phrase[index])) throw invalid(); }
        else if (Character.isLowSurrogate(phrase[index])) throw invalid();
      }
      String plan = null;
      if (apply) {
        String password = fields.get("accountPassword");if (password == null || password.isEmpty() || password.length() > 32768) throw invalid();account = password.toCharArray();
        plan = fields.get("planId");if (plan == null || !plan.matches("[A-Za-z0-9_-]{43}")) throw invalid();
      }
      var result = new VaultRestoreRequest(file, phrase, account, plan, decisions, confirmUpdates);success = true;return result;
    } catch (VaultRestoreFailure expected) { throw expected; }
    catch (Exception ignored) { throw invalid(); }
    finally {
      if (bytes != null) Arrays.fill(bytes, (byte) 0);
      if (!success) { if (file != null) Arrays.fill(file, (byte) 0);wipe(phrase);wipe(account);SLOT.release(); }
    }
  }

  private static List<VaultRestoreDecision> decisions(JsonParser parser) throws Exception {
    if (parser.currentToken() != JsonToken.START_ARRAY) throw invalid();
    var result = new ArrayList<VaultRestoreDecision>();var seen = new HashSet<Integer>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      if (result.size() >= 500 || parser.currentToken() != JsonToken.START_OBJECT) throw invalid();
      Integer index = null;String action = null;UUID target = null;
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalid();String name = parser.currentName();parser.nextToken();
        switch (name) {
          case "sourceIndex" -> { if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) throw invalid();index = parser.getIntValue(); }
          case "action" -> action = string(parser);
          case "targetId" -> { if (parser.currentToken() != JsonToken.VALUE_NULL) { String value = string(parser);target = UUID.fromString(value);if (!target.toString().equals(value)) throw invalid(); } }
          default -> throw invalid();
        }
      }
      if (index == null || index < 0 || index >= 500 || !seen.add(index) || action == null) throw invalid();
      var selected = switch (action) { case "add" -> VaultRestoreDecision.Action.ADD;case "ignore" -> VaultRestoreDecision.Action.IGNORE;case "update" -> VaultRestoreDecision.Action.UPDATE;default -> throw invalid(); };
      if ((selected == VaultRestoreDecision.Action.UPDATE) != (target != null)) throw invalid();
      result.add(new VaultRestoreDecision(index, selected, target));
    }
    return List.copyOf(result);
  }
  private static String string(JsonParser parser) throws Exception { if (parser.currentToken() != JsonToken.VALUE_STRING) throw invalid();return parser.getText(); }
  private static VaultRestoreFailure invalid() { return new VaultRestoreFailure(RESTORE_INVALID_REQUEST); }
  private static void wipe(char[] value) { if (value != null) Arrays.fill(value, '\0'); }
  @Override public void close() { if (!closed) { closed = true;Arrays.fill(file, (byte) 0);wipe(passphrase);wipe(accountPassword);SLOT.release(); } }
  @Override public String toString() { return "VaultRestoreRequest[redacted]"; }
}
