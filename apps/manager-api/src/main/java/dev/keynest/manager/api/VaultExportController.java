package dev.keynest.manager.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.manager.vault.VaultExportAdmission;
import dev.keynest.manager.vault.VaultExportFailure;
import dev.keynest.manager.vault.VaultExportSnapshots;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static dev.keynest.manager.vault.VaultExportFailure.Code.*;

@RestController
public class VaultExportController {
  private final AuthService auth;
  private final PasswordHashingService passwords;
  private final VaultExportSnapshots snapshots;
  private final VaultExportAdmission admission;
  private final EncryptedVaultBackup backups;

  public VaultExportController(AuthService auth, PasswordHashingService passwords, VaultExportSnapshots snapshots,
      VaultExportAdmission admission, EncryptedVaultBackup backups) {
    this.auth = auth;this.passwords = passwords;this.snapshots = snapshots;this.admission = admission;this.backups = backups;
  }

  @PostMapping(path = ApiPaths.VAULT_EXPORT_PREVIEW, produces = MediaType.APPLICATION_JSON_VALUE)
  public VaultExportSnapshots.Preview preview(HttpServletRequest request) {
    String authorization = authorization(request);UserAccount owner = auth.requireUser(authorization);
    try (var body = VaultExportRequest.read(request, false);var work = admission.acquire()) {
      var result = snapshots.preview(owner, body.selection);requireCurrent(authorization, owner);return result;
    }
  }

  @PostMapping(ApiPaths.VAULT_EXPORT_DOWNLOAD)
  public void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String authorization = authorization(request);UserAccount initial = auth.requireUser(authorization);
    byte[] file = null;ObjectNode payload = null;
    try (var body = VaultExportRequest.read(request, true);var work = admission.acquire()) {
      try {
        if (!passwords.verifyAndClear(body.accountPassword, initial.getPasswordHash())) throw new VaultExportFailure(EXPORT_REAUTH_FAILED);
        UserAccount current = requireCurrent(authorization, initial);
        payload = snapshots.preparePayloadForVerifiedUser(current, body.selection, body.snapshot);
        try { file = backups.encrypt(payload, body.backupPassphrase); }
        catch (IllegalStateException ignored) { throw new VaultExportFailure(EXPORT_BUSY); }
        catch (IllegalArgumentException ignored) { throw new VaultExportFailure(EXPORT_DATA_UNAVAILABLE); }
        payload.removeAll();payload = null;
        requireCurrent(authorization, initial);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"keynest-vault.keynest\"");
        response.setContentLengthLong(file.length);
        response.getOutputStream().write(file);
      } finally {
        // Cleanup finishes before returning the bulk-work permit, also on disconnect/rejection.
        if (payload != null) payload.removeAll();
        if (file != null) Arrays.fill(file, (byte) 0);
        body.close();
      }
    }
  }

  private UserAccount requireCurrent(String authorization, UserAccount verified) {
    UserAccount current = auth.requireUser(authorization);
    if (!current.getId().equals(verified.getId())) throw new VaultExportFailure(EXPORT_SESSION_REQUIRED);
    if (!current.getPasswordHash().equals(verified.getPasswordHash())) throw new VaultExportFailure(EXPORT_REAUTH_FAILED);
    return current;
  }

  private static String authorization(HttpServletRequest request) {
    var headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (headers.size() != 1) throw new VaultExportFailure(EXPORT_SESSION_REQUIRED);
    return headers.getFirst();
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> sessionOrCapacity(ResponseStatusException failure) {
    return ResponseEntity.status(failure.getStatusCode()).cacheControl(CacheControl.noStore())
      .body(Map.of("code", failure.getStatusCode().value() == 503 ? "EXPORT_BUSY" : "EXPORT_SESSION_REQUIRED"));
  }

  @ExceptionHandler(VaultExportFailure.class)
  ResponseEntity<Map<String, String>> failure(VaultExportFailure failure) {
    int status = switch (failure.code()) {
      case EXPORT_SESSION_REQUIRED -> 401;
      case EXPORT_REAUTH_FAILED -> 403;
      case EXPORT_SELECTION_UNAVAILABLE -> 404;
      case EXPORT_CHANGED -> 409;
      case EXPORT_REQUEST_TOO_LARGE, EXPORT_LIMIT_EXCEEDED -> 413;
      case EXPORT_EMPTY -> 422;
      case EXPORT_BUSY, EXPORT_DATA_UNAVAILABLE -> 503;
      default -> 400;
    };
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code", failure.code().name()));
  }
}
