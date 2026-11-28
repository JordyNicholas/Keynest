package dev.keynest.manager.auth;

import dev.keynest.manager.api.ApiPaths;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(path = ApiPaths.AUTH, produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<Map<String, String>> register() {
    return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore())
      .body(Map.of("code", "REGISTRATION_REQUIRES_VERIFICATION"));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthService.AuthResponse> login(@RequestBody CredentialsRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
      .body(authService.login(request.email(), request.password()));
  }

  @PostMapping("/logout")
  public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
    authService.logout(authorization);
    return Map.of("status", "ok");
  }

  @PostMapping("/me")
  public Map<String, Object> me(@RequestHeader("Authorization") String authorization) {
    UserAccount user = authService.requireUser(authorization);
    return Map.of("userId", user.getId(), "email", user.getEmail(), "emailVerified", user.isEmailVerified());
  }

  public record CredentialsRequest(@NotBlank @Email String email, @NotBlank String password) {
    public CredentialsRequest {
      email = email == null ? null : email.trim();
    }
  }
}
