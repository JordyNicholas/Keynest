package dev.keynest.manager.auth;

import dev.keynest.manager.config.LoginThrottleProperties;
import dev.keynest.manager.security.PasswordHashingService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginThrottleFailureTest {
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  private final PasswordHashingService passwords = mock(PasswordHashingService.class);

  private AuthService auth(int failures) {
    var settings = new LoginThrottleProperties();
    settings.setFailureLimit(failures);
    when(passwords.hash(anyString())).thenReturn("dummy-hash");
    return new AuthService(users, sessions, passwords, new LoginAttemptLimiter(settings),
      mock(jakarta.persistence.EntityManager.class));
  }

  @Test
  void busyVerifierReleasesAdmissionAndDoesNotCountAsABadPassword() {
    var auth = auth(1);
    when(passwords.verify(anyString(), anyString()))
      .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).thenReturn(false);
    status(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.SERVICE_UNAVAILABLE);
    status(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    status(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.TOO_MANY_REQUESTS);
    verify(passwords, times(2)).verify(anyString(), anyString());
    verifyNoInteractions(sessions);
  }

  @Test
  void repositoryFailureReleasesAdmissionWithoutConsumingAFailure() {
    var auth = auth(1);
    when(users.findByEmailIgnoreCase(anyString())).thenThrow(new IllegalStateException("synthetic repository failure"))
      .thenReturn(Optional.empty());
    assertThatThrownBy(() -> auth.login("missing@example.invalid", "synthetic candidate"))
      .isInstanceOf(IllegalStateException.class);
    status(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    status(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.TOO_MANY_REQUESTS);
    verify(passwords).verify(anyString(), anyString());
    verifyNoInteractions(sessions);
  }

  @Test
  void sessionWriteFailureReleasesAdmissionAndPreservesExistingFailureHistory() {
    var auth = auth(2);
    var account = dev.keynest.manager.testing.TestAccounts.account(UUID.randomUUID(), "known@example.invalid",
      "stored-hash", Instant.now());
    when(users.findByEmailIgnoreCase(account.getEmail())).thenReturn(Optional.of(account));
    when(users.findForPolicyUpdate(account.getId())).thenReturn(Optional.of(account));
    when(passwords.verify("correct synthetic candidate", "stored-hash")).thenReturn(true);
    status(() -> auth.login(account.getEmail(), "wrong"), HttpStatus.UNAUTHORIZED);
    when(sessions.save(any())).thenThrow(new IllegalStateException("synthetic session failure"));
    assertThatThrownBy(() -> auth.login(account.getEmail(), "correct synthetic candidate"))
      .isInstanceOf(IllegalStateException.class);
    status(() -> auth.login(account.getEmail(), "wrong"), HttpStatus.UNAUTHORIZED);
    status(() -> auth.login(account.getEmail(), "correct synthetic candidate"), HttpStatus.TOO_MANY_REQUESTS);
    verify(sessions).save(any());
  }

  @Test
  void normalizationDoesNotDependOnTheServerLocale() {
    var auth = auth(1);
    java.util.Locale original = java.util.Locale.getDefault();
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
      status(() -> auth.login(" Identity@example.invalid ", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
      status(() -> auth.login("identity@example.invalid", "synthetic candidate"), HttpStatus.TOO_MANY_REQUESTS);
      verify(users).findByEmailIgnoreCase("identity@example.invalid");
    } finally {
      java.util.Locale.setDefault(original);
    }
  }

  private static void status(Runnable request, HttpStatus expected) {
    assertThatThrownBy(request::run).isInstanceOfSatisfying(ResponseStatusException.class,
      failure -> assertThat(failure.getStatusCode()).isEqualTo(expected));
  }
}
