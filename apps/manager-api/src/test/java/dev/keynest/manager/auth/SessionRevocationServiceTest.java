package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.keynest.manager.security.PasswordHashingService;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SessionRevocationServiceTest {
  private final AuthService auth = mock(AuthService.class);
  private final PasswordHashingService passwords = mock(PasswordHashingService.class);
  private final SessionRevocationTransactions transactions = mock(SessionRevocationTransactions.class);
  private final UserAccount owner = new UserAccount(UUID.randomUUID(), "synthetic@example.invalid", "stored-hash", Instant.now());
  private final SessionRevocationService service = new SessionRevocationService(auth, passwords, transactions);

  @Test
  void validProofUsesOnlyServerIdentityAndAlwaysClearsPassword() {
    when(auth.requireUser("Bearer synthetic")).thenReturn(owner);
    when(passwords.verifyAndClear(any(), eq("stored-hash"))).thenReturn(true);
    char[] password = "synthetic proof".toCharArray();
    service.revokeAll("Bearer synthetic", password);
    assertThat(Arrays.equals(password, new char[password.length])).isTrue();
    verify(transactions).revoke(owner.getId(), "stored-hash", SessionTokenDigest.digest("synthetic"));
  }

  @Test
  void wrongPasswordAndInvalidSessionCannotReachMutationAndAreCleared() {
    when(auth.requireUser("Bearer synthetic")).thenReturn(owner);
    char[] password = "synthetic wrong proof".toCharArray();
    status(() -> service.revokeAll("Bearer synthetic", password), HttpStatus.FORBIDDEN);
    assertThat(Arrays.equals(password, new char[password.length])).isTrue();
    when(auth.requireUser("Bearer invalid")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    char[] invalid = "synthetic proof".toCharArray();
    status(() -> service.revokeAll("Bearer invalid", invalid), HttpStatus.UNAUTHORIZED);
    assertThat(Arrays.equals(invalid, new char[invalid.length])).isTrue();
    verifyNoInteractions(transactions);
  }

  @Test
  void transactionFailureClearsCredentialsAndReleasesAdmission() {
    when(auth.requireUser("Bearer synthetic")).thenReturn(owner);
    when(passwords.verifyAndClear(any(), anyString())).thenReturn(true);
    doThrow(new IllegalStateException("synthetic failure")).doNothing().when(transactions).revoke(any(), anyString(), anyString());
    char[] first = "synthetic proof".toCharArray();
    assertThatThrownBy(() -> service.revokeAll("Bearer synthetic", first)).isInstanceOf(IllegalStateException.class);
    assertThat(Arrays.equals(first, new char[first.length])).isTrue();
    service.revokeAll("Bearer synthetic", "synthetic proof".toCharArray());
    verify(transactions, times(2)).revoke(any(), anyString(), anyString());
  }

  @Test
  void competingRequestIsRejectedWithoutQueuingAndPasswordIsCleared() throws Exception {
    when(auth.requireUser("Bearer synthetic")).thenReturn(owner);
    var entered = new CountDownLatch(1);var release = new CountDownLatch(1);
    when(passwords.verifyAndClear(any(), anyString())).thenAnswer(call -> {
      entered.countDown();assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();return true;
    });
    try (var pool = Executors.newSingleThreadExecutor()) {
      var first = pool.submit(() -> service.revokeAll("Bearer synthetic", "synthetic proof".toCharArray()));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        char[] second = "another proof".toCharArray();
        status(() -> service.revokeAll("Bearer synthetic", second), HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Arrays.equals(second, new char[second.length])).isTrue();
      } finally { release.countDown(); }
      first.get(10, TimeUnit.SECONDS);
    }
    verify(passwords).verifyAndClear(any(), anyString());
  }

  private static void status(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
      failure -> assertThat(failure.getStatusCode()).isEqualTo(status));
  }
}
