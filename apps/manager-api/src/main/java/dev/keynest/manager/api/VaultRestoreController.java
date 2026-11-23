package dev.keynest.manager.api;

import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.manager.vault.VaultRestoreFailure;
import dev.keynest.manager.vault.VaultRestorePlans;
import dev.keynest.manager.vault.VaultRestoreService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static dev.keynest.manager.vault.VaultRestoreFailure.Code.*;

@RestController
public class VaultRestoreController {
  private final AuthService auth;
  private final PasswordHashingService passwords;
  private final VaultRestoreService restore;
  private final VaultRestorePlans plans;
  public VaultRestoreController(AuthService auth, PasswordHashingService passwords, VaultRestoreService restore, VaultRestorePlans plans) {
    this.auth = auth;this.passwords = passwords;this.restore = restore;this.plans = plans;
  }
  @PostMapping(ApiPaths.VAULT_RESTORE_PREVIEW)
  public VaultRestorePlans.Ticket preview(HttpServletRequest request) {
    String authorization = authorization(request);UserAccount initial = auth.requireUser(authorization);
    try (var body = VaultRestoreRequest.read(request, false)) {
      var preview = restore.preview(initial.getId(), body.file, body.passphrase);
      return plans.issue(VaultRestorePlans.binding(authorization, requireCurrent(authorization, initial)), preview);
    }
  }
  @PostMapping(ApiPaths.VAULT_RESTORE_APPLY)
  public VaultRestoreService.Result apply(HttpServletRequest request) {
    String authorization = authorization(request);UserAccount initial = auth.requireUser(authorization);
    try (var body = VaultRestoreRequest.read(request, true)) {
      if (!passwords.verifyAndClear(body.accountPassword, initial.getPasswordHash())) throw new VaultRestoreFailure(RESTORE_REAUTH_FAILED);
      UserAccount current = requireCurrent(authorization, initial);
      var plan = plans.consume(body.planId, VaultRestorePlans.binding(authorization, current));
      return restore.restore(current.getId(), body.file, body.passphrase, plan.snapshot(), () -> requireCurrent(authorization, initial));
    }
  }
  private UserAccount requireCurrent(String authorization, UserAccount initial) {
    UserAccount current;
    try { current = auth.requireUser(authorization); }
    catch (ResponseStatusException ignored) { throw new VaultRestoreFailure(RESTORE_SESSION_REQUIRED); }
    if (!current.getId().equals(initial.getId())) throw new VaultRestoreFailure(RESTORE_SESSION_REQUIRED);
    if (!current.getPasswordHash().equals(initial.getPasswordHash())) throw new VaultRestoreFailure(RESTORE_REAUTH_FAILED);
    return current;
  }
  private static String authorization(HttpServletRequest request) {
    var headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (headers.size() != 1) throw new VaultRestoreFailure(RESTORE_SESSION_REQUIRED);return headers.getFirst();
  }
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> session(ResponseStatusException failure) {
    return ResponseEntity.status(failure.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of("code", failure.getStatusCode().value() == 503 ? "RESTORE_BUSY" : "RESTORE_SESSION_REQUIRED"));
  }
  @ExceptionHandler(VaultRestoreFailure.class)
  ResponseEntity<Map<String, String>> failure(VaultRestoreFailure failure) {
    int status = switch (failure.code()) {
      case RESTORE_SESSION_REQUIRED -> 401;
      case RESTORE_REAUTH_FAILED -> 403;
      case RESTORE_OWNER_UNAVAILABLE -> 404;
      case RESTORE_CHANGED, RESTORE_PLAN_INVALID -> 409;
      case RESTORE_LIMIT_EXCEEDED, RESTORE_REQUEST_TOO_LARGE -> 413;
      case RESTORE_EMPTY -> 422;
      case RESTORE_BUSY, RESTORE_FAILED -> 503;
      default -> 400;
    };
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code", failure.code().name()));
  }
}
