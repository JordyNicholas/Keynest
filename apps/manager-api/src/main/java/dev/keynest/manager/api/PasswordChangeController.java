package dev.keynest.manager.api;

import dev.keynest.manager.auth.PasswordChangeService;
import dev.keynest.manager.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PasswordChangeController {
  private final PasswordChangeService sessions;
  public PasswordChangeController(PasswordChangeService sessions) { this.sessions = sessions; }

  @PostMapping(path = ApiPaths.AUTH_PASSWORD_CHANGE, produces = "application/json")
  public ResponseEntity<AuthService.AuthResponse> change(@RequestHeader("Authorization") String authorization, HttpServletRequest request) {
    try (var input = PasswordChangeRequest.read(request)) {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(sessions.change(authorization, input.currentPassword, input.newPassword));
    }
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> refused(ResponseStatusException failure) {
    String code = switch (failure.getStatusCode().value()) {
      case 400 -> "INVALID_REQUEST";
      case 401 -> "SESSION_REQUIRED";
      case 403 -> "PASSWORD_VERIFICATION_FAILED";
      case 413 -> "REQUEST_TOO_LARGE";
      case 429 -> "PASSWORD_CHANGE_BUSY";
      case 503 -> "PASSWORD_OPERATIONS_BUSY";
      default -> "PASSWORD_CHANGE_UNAVAILABLE";
    };
    return ResponseEntity.status(failure.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of("code", code));
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<Map<String, String>> unavailable(RuntimeException failure) {
    return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(Map.of("code", "PASSWORD_CHANGE_UNAVAILABLE"));
  }
}
