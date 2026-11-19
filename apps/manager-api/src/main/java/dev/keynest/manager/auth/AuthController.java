package dev.keynest.manager.auth;

import dev.keynest.manager.api.ApiPaths;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping(path = ApiPaths.AUTH, produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthService.AuthResponse> register(
            @Valid @RequestBody CredentialsRequest request, Errors errors
    ) {
        if (errors.hasErrors()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid registration input");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthService.AuthResponse> login(@RequestBody CredentialsRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
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
        return Map.of(
                "userId", user.getId(),
                "email", user.getEmail()
        );
    }

    public record CredentialsRequest(@NotBlank @Email String email, @NotBlank String password) {
        public CredentialsRequest {
            email = email == null ? null : email.trim();
        }
    }
}
