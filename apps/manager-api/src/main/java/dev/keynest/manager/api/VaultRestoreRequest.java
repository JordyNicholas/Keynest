package dev.keynest.manager.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.keynest.manager.vault.VaultRestoreFailure;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.springframework.http.MediaType;
import static dev.keynest.manager.vault.VaultRestoreFailure.Code.*;

/** Single admitted bounded body/file, held through cleanup. No multipart/temp files or queued bodies. */
final class VaultRestoreRequest implements AutoCloseable {
  private static final int MAX_BASE64 = ((EncryptedVaultBackup.MAX_FILE_BYTES + 2) / 3) * 4;
  static final int MAX_BYTES = MAX_BASE64 + 256 * 1024;
  private static final Semaphore SLOT = new Semaphore(1);
  private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
    StreamReadConstraints.builder().maxNestingDepth(2).maxStringLength(MAX_BASE64).maxNumberLength(10).build()).build())
    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final Set<String> PREVIEW = Set.of("file", "backupPassphrase");
  private static final Set<String> APPLY = Set.of("file", "backupPassphrase", "accountPassword", "planId");
  final byte[] file;
  final char[] passphrase;
  final char[] accountPassword;
  final String planId;
  private boolean closed;
  private VaultRestoreRequest(byte[] file, char[] phrase, char[] account, String plan) { this.file = file;this.passphrase = phrase;this.accountPassword = account;this.planId = plan; }

  static VaultRestoreRequest read(HttpServletRequest request, boolean apply) {
    if (!SLOT.tryAcquire()) throw new VaultRestoreFailure(RESTORE_BUSY);
    byte[] bytes = null, file = null;char[] phrase = null, account = null;boolean success = false;
    try {
      if (request.getQueryString() != null || request.getContentType() == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) throw invalid();
      bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new VaultRestoreFailure(RESTORE_REQUEST_TOO_LARGE);
      JsonNode body = JSON.readTree(bytes);if (body == null || !body.isObject()) throw invalid();
      var names = body.fieldNames();while (names.hasNext()) if (!(apply ? APPLY : PREVIEW).contains(names.next())) throw invalid();
      String encoded = text(body, "file");if (encoded.isEmpty() || encoded.length() > MAX_BASE64) throw new VaultRestoreFailure(RESTORE_REQUEST_TOO_LARGE);
      file = Base64.getDecoder().decode(encoded);
      if (file.length == 0 || file.length > EncryptedVaultBackup.MAX_FILE_BYTES) throw new VaultRestoreFailure(RESTORE_REQUEST_TOO_LARGE);
      if (!Base64.getEncoder().encodeToString(file).equals(encoded)) throw invalid();
      String backup = text(body, "backupPassphrase");
      if (backup.length() > 1024 || backup.codePointCount(0, backup.length()) < 15 || backup.isBlank()) throw invalid();
      phrase = backup.toCharArray();
      for (int index = 0; index < phrase.length; index++) {
        if (Character.isHighSurrogate(phrase[index])) { if (++index >= phrase.length || !Character.isLowSurrogate(phrase[index])) throw invalid(); }
        else if (Character.isLowSurrogate(phrase[index])) throw invalid();
      }
      String plan = null;
      if (apply) {
        String password = text(body, "accountPassword");if (password.isEmpty() || password.length() > 32768) throw invalid();account = password.toCharArray();
        plan = text(body, "planId");if (!plan.matches("[A-Za-z0-9_-]{43}")) throw invalid();
      }
      var result = new VaultRestoreRequest(file, phrase, account, plan);success = true;return result;
    } catch (VaultRestoreFailure expected) { throw expected; }
    catch (Exception ignored) { throw invalid(); }
    finally {
      if (bytes != null) Arrays.fill(bytes, (byte) 0);
      if (!success) { if (file != null) Arrays.fill(file, (byte) 0);wipe(phrase);wipe(account);SLOT.release(); }
    }
  }
  private static String text(JsonNode body, String field) { if (!body.path(field).isTextual()) throw invalid();return body.path(field).textValue(); }
  private static VaultRestoreFailure invalid() { return new VaultRestoreFailure(RESTORE_INVALID_REQUEST); }
  private static void wipe(char[] value) { if (value != null) Arrays.fill(value, '\0'); }
  @Override public void close() { if (!closed) { closed = true;Arrays.fill(file, (byte) 0);wipe(passphrase);wipe(accountPassword);SLOT.release(); } }
  @Override public String toString() { return "VaultRestoreRequest[redacted]"; }
}
