package dev.keynest.manager.auth;

import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.manager.config.LoginThrottleProperties;
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
  private final UserAccount known = dev.keynest.manager.testing.TestAccounts.account(UUID.randomUUID(),
    "known@example.invalid", "stored-hash", Instant.now());

  private AuthService auth() {
    when(passwords.hash(anyString())).thenReturn("dummy-hash");
    when(users.findByEmailIgnoreCase(known.getEmail())).thenReturn(Optional.of(known));
    when(users.findForPolicyUpdate(known.getId())).thenReturn(Optional.of(known));
    return new AuthService(users, sessions, passwords, new LoginAttemptLimiter(new LoginThrottleProperties()),
      mock(jakarta.persistence.EntityManager.class));
  }

  @Test
  void eligibilityIsRecheckedUnderTheOwnerLockBeforeIssuingASession() {
    AuthService auth = auth();
    when(passwords.verify("synthetic candidate", "stored-hash")).thenReturn(true);
    when(users.findForPolicyUpdate(known.getId()))
      .thenReturn(Optional.of(new UserAccount(known.getId(), known.getEmail(), "stored-hash", Instant.now())));
    refused(() -> auth.login(known.getEmail(), "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(sessions);
  }

  @Test
  void correctCredentialsCannotAuthenticateAnIneligibleAccountOrAnExistingBearer() {
    AuthService auth = auth();
    UserAccount ineligible = new UserAccount(known.getId(), known.getEmail(), "stored-hash", Instant.now());
    when(users.findByEmailIgnoreCase(known.getEmail())).thenReturn(Optional.of(ineligible));
    when(passwords.verify("synthetic candidate", "stored-hash")).thenReturn(true);
    refused(() -> auth.login(known.getEmail(), "synthetic candidate"), HttpStatus.UNAUTHORIZED);
    verify(passwords).verify("synthetic candidate", "stored-hash");
    verifyNoInteractions(sessions);
    String bearer = "synthetic-ineligible-bearer";
    when(sessions.findByTokenDigest(SessionTokenDigest.digest(bearer)))
      .thenReturn(Optional.of(new AuthSession(UUID.randomUUID(), known.getId(), SessionTokenDigest.digest(bearer),
        Instant.now().plusSeconds(60), Instant.now())));
    when(users.findById(known.getId())).thenReturn(Optional.of(ineligible));
    assertThatThrownBy(() -> auth.requireUser("Bearer " + bearer)).isInstanceOfSatisfying(ResponseStatusException.class,
      failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
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
    for (String email : new String[]{known.getEmail(), "missing@example.invalid"}) {
      for (String candidate : new String[]{null, "", " "}) {
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
    AuthService auth = new AuthService(users, sessions, shared, new LoginAttemptLimiter(new LoginThrottleProperties()),
      mock(jakarta.persistence.EntityManager.class));
    when(users.findByEmailIgnoreCase(known.getEmail())).thenReturn(Optional.of(known));
    when(users.findForPolicyUpdate(known.getId())).thenReturn(Optional.of(known));
    CountDownLatch occupied = new CountDownLatch(1), release = new CountDownLatch(1);
    when(hasher.hashPassword(any(char[].class))).thenAnswer(call -> {
      occupied.countDown();
      assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
      return "work complete";
    });
    try (var worker = Executors.newSingleThreadExecutor()) {
      var hashing = worker.submit(() -> shared.hash("synthetic concurrent work"));
      try {
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        refused(() -> auth.login(known.getEmail(), "synthetic candidate"), HttpStatus.SERVICE_UNAVAILABLE);
        refused(() -> auth.login("missing@example.invalid", "synthetic candidate"), HttpStatus.SERVICE_UNAVAILABLE);
        verify(hasher, never()).verifyPassword(any(), anyString());
      } finally {
        release.countDown();
      }
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
    var account = dev.keynest.manager.testing.TestAccounts.account(UUID.randomUUID(), "native@example.invalid",
      real.hash(password), Instant.now());
    when(users.findByEmailIgnoreCase(account.getEmail())).thenReturn(Optional.of(account));
    when(users.findForPolicyUpdate(account.getId())).thenReturn(Optional.of(account));
    AuthService auth = new AuthService(users, sessions, real, new LoginAttemptLimiter(new LoginThrottleProperties()),
      mock(jakarta.persistence.EntityManager.class));
    refused(() -> auth.login(account.getEmail(), "wrong synthetic credential"), HttpStatus.UNAUTHORIZED);
    refused(() -> auth.login("missing@example.invalid", "wrong synthetic credential"), HttpStatus.UNAUTHORIZED);
    // Even knowing the public non-account work input must not authenticate.
    refused(() -> auth.login("missing@example.invalid", "keynest missing-account verification"),
      HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(sessions);
    assertThat(auth.login(account.getEmail(), password).userId()).isEqualTo(account.getId());
  }

  private static void refused(Runnable call, HttpStatus status) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
      assertThat(failure.getStatusCode()).isEqualTo(status);
      assertThat(failure.getReason())
        .isEqualTo(status == HttpStatus.UNAUTHORIZED ? "invalid credentials" : "password operations are busy");
    });
  }
}
