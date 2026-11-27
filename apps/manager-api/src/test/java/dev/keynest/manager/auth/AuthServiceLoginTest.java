package dev.keynest.manager.auth;

import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.seal.PasswordHasher;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceLoginTest {
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  private final PasswordHashingService passwords = mock(PasswordHashingService.class);
  private final UserAccount known = new UserAccount(UUID.randomUUID(), "known@example.invalid", "stored-hash", Instant.now());

  private AuthService auth() {
    when(passwords.hash(anyString())).thenReturn("dummy-hash");
    when(users.findByEmailIgnoreCase(known.getEmail())).thenReturn(Optional.of(known));
    return new AuthService(users, sessions, passwords);
  }

  @Test
  void missingAccountsVerifyOncePerAttemptWithOnePreparedHash() {
    AuthService auth = auth();
    refused(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    refused(() -> auth.login("other@example.invalid", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    verify(passwords, times(1)).hash(anyString());
    verify(passwords, times(2)).verify("synthetic candidate", "dummy-hash");
    verifyNoInteractions(sessions);
    verify(users, never()).save(any());
  }

  @Test
  void aMatchingDummyCanNeverCreateASession() {
    AuthService auth = auth();
    when(passwords.verify("synthetic candidate", "dummy-hash")).thenReturn(true);
    refused(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    verify(passwords).verify("synthetic candidate", "dummy-hash");
    verifyNoInteractions(sessions);
    verify(users, never()).save(any());
  }

  @Test
  void aKnownWrongPasswordUsesOnlyTheStoredHashAndTheSameRefusal() {
    AuthService auth = auth();
    refused(() -> auth.login(" KNOWN@example.invalid ", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    verify(passwords).verify("synthetic candidate", "stored-hash");
    verify(passwords, never()).verify(anyString(), eq("dummy-hash"));
    verifyNoInteractions(sessions);
  }

  @Test
  void knownLegacyPasswordsStillIssueADigestOnlySession() {
    AuthService auth = auth();
    when(passwords.verify("short", "stored-hash")).thenReturn(true);
    AuthService.AuthResponse result = auth.login(known.getEmail(), "short");
    assertThat(result.userId()).isEqualTo(known.getId());
    var session = org.mockito.ArgumentCaptor.forClass(AuthSession.class);
    verify(sessions).save(session.capture());
    assertThat(session.getValue().getTokenDigest().equals(SessionTokenDigest.digest(result.token()))).isTrue();
    verify(passwords).verify("short", "stored-hash");
    verify(passwords, never()).verify(anyString(), eq("dummy-hash"));
  }

  @Test
  void invalidInputHasTheSamePreLookupRefusalRegardlessOfAccount() {
    AuthService auth = auth();
    for (String email : new String[] {known.getEmail(), "missing@example.invalid"}) {
      for (String candidate : new String[] {null, "", " "}) {
        refused(() -> auth.login(email, candidate), HttpStatus.UNAUTHORIZED);
      }
    }
    refused(() -> auth.login(null, "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    refused(() -> auth.login(" ", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    verify(users, never()).findByEmailIgnoreCase(anyString());
    verify(passwords, never()).verify(anyString(), anyString());
    verifyNoInteractions(sessions);
  }

  @Test
  void sharedCapacityDenialDoesNotDependOnAccountExistenceAndRecovers() throws Exception {
    PasswordHasher hasher = mock(PasswordHasher.class);
    when(hasher.hashPassword(any(char[].class))).thenReturn("dummy-hash");
    PasswordHashingService shared = new PasswordHashingService(hasher, 1);
    AuthService auth = new AuthService(users, sessions, shared);
    when(users.findByEmailIgnoreCase(known.getEmail())).thenReturn(Optional.of(known));
    CountDownLatch occupied = new CountDownLatch(1), release = new CountDownLatch(1);
    when(hasher.hashPassword(any(char[].class))).thenAnswer(call -> {
      occupied.countDown();assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();return "work complete";
    });
    try (var worker = Executors.newSingleThreadExecutor()) {
      var hashing = worker.submit(() -> shared.hash("synthetic concurrent work"));
      try {
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        refused(() -> auth.login(known.getEmail(), "synthetic candidate"), HttpStatus.SERVICE_UNAVAILABLE);
        refused(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.SERVICE_UNAVAILABLE);
        verify(hasher, never()).verifyPassword(any(), anyString());
      } finally { release.countDown(); }
      hashing.get(5, TimeUnit.SECONDS);
    }
    refused(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    verify(hasher).verifyPassword(any(), eq("dummy-hash"));
    verifyNoInteractions(sessions);
  }

  @Test
  void actualArgon2UnknownAndKnownFailuresCannotCreateSessions() {
    PasswordHashingService real = new PasswordHashingService(new PasswordHasher(), 1);
    String password = "Synthetic known passphrase!42";
    var account = new UserAccount(UUID.randomUUID(), "native@example.invalid", real.hash(password), Instant.now());
    when(users.findByEmailIgnoreCase(account.getEmail())).thenReturn(Optional.of(account));
    AuthService auth = new AuthService(users, sessions, real);
    refused(() -> auth.login(account.getEmail(), "wrong synthetic credential"), HttpStatus.UNAUTHORIZED);
    refused(() -> auth.login("missing@example.invalid", "wrong synthetic credential"), HttpStatus.UNAUTHORIZED);
    // Even knowing the public non-account work input must not authenticate.
    refused(() -> auth.login("missing@example.invalid", "keynest missing-account verification"), HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(sessions);
    assertThat(auth.login(account.getEmail(), password).userId()).isEqualTo(account.getId());
  }

  private static void refused(Runnable call, HttpStatus status) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
      assertThat(failure.getStatusCode()).isEqualTo(status);
      assertThat(failure.getReason()).isEqualTo(status == HttpStatus.UNAUTHORIZED ? "invalid credentials" : "password operations are busy");
    });
  }
}
