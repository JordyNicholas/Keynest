package dev.keynest.manager.api;

import dev.keynest.manager.auth.SessionRevocationService;
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
public class SessionRevocationController {
  private final SessionRevocationService sessions;
  public SessionRevocationController(SessionRevocationService sessions) { this.sessions = sessions; }

  @PostMapping(path = ApiPaths.AUTH_LOGOUT_ALL, produces = "application/json")
  public ResponseEntity<Map<String, String>> revoke(@RequestHeader("Authorization") String authorization, HttpServletRequest request) {
    try (var input = SessionRevocationRequest.read(request)) {
      sessions.revokeAll(authorization, input.password);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status", "ok"));
    }
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> refused(ResponseStatusException failure) {
    String code = switch (failure.getStatusCode().value()) {
      case 400 -> "INVALID_REQUEST";
      case 401 -> "SESSION_REQUIRED";
      case 403 -> "PASSWORD_VERIFICATION_FAILED";
      case 413 -> "REQUEST_TOO_LARGE";
      case 429 -> "REVOCATION_BUSY";
      case 503 -> "PASSWORD_OPERATIONS_BUSY";
      default -> "REVOCATION_UNAVAILABLE";
    };
    return ResponseEntity.status(failure.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of("code", code));
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<Map<String, String>> unavailable(RuntimeException failure) {
    return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(Map.of("code", "REVOCATION_UNAVAILABLE"));
  }
}
