package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.keynest.manager.security.PasswordHashingService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresSessionRevocationIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  dev.keynest.manager.testing.TestAccounts accounts;

  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    .withDatabaseName("session_revocation").withUsername("keynest").withPassword("disposable_test");
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }
  @Autowired
  AuthService auth;
  @Autowired
  SessionRevocationService revocation;
  @Autowired
  SessionRevocationTransactions revocationTransactions;
  @Autowired
  UserAccountRepository users;
  @Autowired
  AuthSessionRepository sessions;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  PlatformTransactionManager transactions;
  @MockitoSpyBean
  PasswordHashingService passwords;
  private static final String PASSWORD = "Synthetic account passphrase!42";

  private AuthService.AuthResponse register() {
    return accounts.register(UUID.randomUUID() + "@example.invalid", PASSWORD);
  }
  private String bearer(AuthService.AuthResponse session) {
    return "Bearer " + session.token();
  }
  private void revoke(AuthService.AuthResponse session) {
    revocation.revokeAll(bearer(session), PASSWORD.toCharArray());
  }

  @Test
  void verifiesOutsideMutationTransactionAndPreservesAnotherOwner() {
    var owner = register();
    var second = auth.login(owner.email(), PASSWORD);
    var other = register();
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return call.callRealMethod();
    }).when(passwords).verifyAndClear(any(), anyString());
    revoke(owner);
    assertThat(status(() -> auth.requireUser(bearer(owner)))).isEqualTo(401);
    assertThat(status(() -> auth.requireUser(bearer(second)))).isEqualTo(401);
    assertThat(auth.requireUser(bearer(other)).getId()).isEqualTo(other.userId());
    assertThat(auth.login(owner.email(), PASSWORD).userId()).isEqualTo(owner.userId());
  }

  @Test
  void revokedActingSessionAfterPasswordProofCannotRemoveOtherSessions() throws Exception {
    var owner = register();
    var otherSession = auth.login(owner.email(), PASSWORD);
    var verified = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    holdProof(verified, release);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var result = pool.submit(() -> status(() -> revoke(owner)));
      try {
        await(verified);
        auth.logout(bearer(owner));
      } finally {
        release.countDown();
      }
      assertThat(result.get(15, TimeUnit.SECONDS)).isEqualTo(401);
    }
    assertThat(auth.requireUser(bearer(otherSession)).getId()).isEqualTo(owner.userId());
  }

  @Test
  void expiredActingSessionAfterProofCannotRevokeOthers() throws Exception {
    var owner = register();
    var other = auth.login(owner.email(), PASSWORD);
    var verified = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    holdProof(verified, release);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var result = pool.submit(() -> status(() -> revoke(owner)));
      try {
        await(verified);
        jdbc.update("UPDATE auth_sessions SET expires_at = ? WHERE token_digest = ?",
          java.sql.Timestamp.from(Instant.now().minusSeconds(1)), SessionTokenDigest.digest(owner.token()));
      } finally {
        release.countDown();
      }
      assertThat(result.get(15, TimeUnit.SECONDS)).isEqualTo(401);
    }
    assertThat(auth.requireUser(bearer(other)).getId()).isEqualTo(owner.userId());
  }

  @Test
  void changedCredentialAfterProofCannotRevokeAndDoesNotOverwriteIt() throws Exception {
    var owner = register();
    String changed = passwords.hash("Synthetic replacement password!42");
    var verified = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    holdProof(verified, release);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var result = pool.submit(() -> status(() -> revoke(owner)));
      try {
        await(verified);
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", changed, owner.userId());
      } finally {
        release.countDown();
      }
      assertThat(result.get(15, TimeUnit.SECONDS)).isEqualTo(401);
    }
    assertThat(auth.requireUser(bearer(owner)).getId()).isEqualTo(owner.userId());
    assertThat(users.findById(owner.userId()).orElseThrow().getPasswordHash().equals(changed)).isTrue();
  }

  @Test
  void racingIndependentWorkersCanConsumeTheActingAuthorityOnlyOnce() throws Exception {
    var owner = register();
    var second = auth.login(owner.email(), PASSWORD);
    var independent = new SessionRevocationService(auth, passwords, revocationTransactions);
    var verified = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    holdProof(verified, release);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> status(() -> revoke(owner)));
      var next = pool.submit(() -> status(() -> independent.revokeAll(bearer(second), PASSWORD.toCharArray())));
      try {
        await(verified);
      } finally {
        release.countDown();
      }
      assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), next.get(15, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(200, 401);
    }
    assertThat(sessions.findByTokenDigest(SessionTokenDigest.digest(owner.token())).isEmpty()).isTrue();
    assertThat(sessions.findByTokenDigest(SessionTokenDigest.digest(second.token())).isEmpty()).isTrue();
  }

  @Test
  void loginVerifiedBeforeRevocationCanIssueOnlyAsANewSignInAfterIt() throws Exception {
    var owner = register();
    var verified = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(call -> {
      boolean valid = (boolean) call.callRealMethod();
      verified.countDown();
      await(release);
      return valid;
    }).when(passwords).verify(eq(PASSWORD), anyString());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var login = pool.submit(() -> auth.login(owner.email(), PASSWORD));
      try {
        await(verified);
        revoke(owner);
      } finally {
        release.countDown();
      }
      var fresh = login.get(15, TimeUnit.SECONDS);
      assertThat(auth.requireUser(bearer(fresh)).getId()).isEqualTo(owner.userId());
    }
    assertThat(status(() -> auth.requireUser(bearer(owner)))).isEqualTo(401);
  }

  @Test
  void loginRefreshRejectsCredentialChangedWhileItsOldEntityIsManaged() throws Exception {
    var owner = register();
    String changed = passwords.hash("Synthetic replacement password!42");
    var verified = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(call -> {
      boolean valid = (boolean) call.callRealMethod();
      verified.countDown();
      await(release);
      return valid;
    }).when(passwords).verify(eq(PASSWORD), anyString());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var login = pool.submit(() -> status(() -> auth.login(owner.email(), PASSWORD)));
      try {
        await(verified);
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", changed, owner.userId());
      } finally {
        release.countDown();
      }
      assertThat(login.get(15, TimeUnit.SECONDS)).isEqualTo(401);
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id = ?", Long.class, owner.userId()))
      .isEqualTo(1L);
  }

  @Test
  void loginAndRevocationBothWaitForTheSameParentAccountLock() throws Exception {
    var owner = register();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
        users.findForPolicyUpdate(owner.userId()).orElseThrow();
        locked.countDown();
        await(release);
        return null;
      }));
      try {
        await(locked);
        var login = pool.submit(() -> auth.login(owner.email(), PASSWORD));
        var revoke = pool.submit(() -> status(() -> revoke(owner)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        long waiters = 0;
        while (System.nanoTime() < deadline) {
          waiters = jdbc.queryForObject("""
            SELECT COUNT(*) FROM pg_stat_activity
            WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE '%users%'
            """, Long.class);
          if (waiters >= 2)
            break;
          Thread.sleep(10);
        }
        assertThat(waiters).as("both operations wait for the account lock").isGreaterThanOrEqualTo(2);
        release.countDown();
        holder.get(15, TimeUnit.SECONDS);
        assertThat(revoke.get(15, TimeUnit.SECONDS)).isEqualTo(200);
        login.get(15, TimeUnit.SECONDS);
        assertThat(status(() -> auth.requireUser(bearer(owner)))).isEqualTo(401);
      } finally {
        release.countDown();
      }
    }
  }

  @Test
  void ownerMismatchAndRollbackCannotRevokeAnUnrelatedAccount() {
    var owner = register();
    var other = register();
    String verifiedHash = users.findById(owner.userId()).orElseThrow().getPasswordHash();
    assertThat(status(
      () -> revocationTransactions.revoke(owner.userId(), verifiedHash, SessionTokenDigest.digest(other.token()))))
      .isEqualTo(401);
    assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
      revocationTransactions.revoke(owner.userId(), verifiedHash, SessionTokenDigest.digest(owner.token()));
      throw new IllegalStateException("synthetic outer rollback");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(auth.requireUser(bearer(owner)).getId()).isEqualTo(owner.userId());
    assertThat(auth.requireUser(bearer(other)).getId()).isEqualTo(other.userId());
  }

  private void holdProof(CountDownLatch verified, CountDownLatch release) {
    doAnswer(call -> {
      boolean valid = (boolean) call.callRealMethod();
      verified.countDown();
      await(release);
      return valid;
    }).when(passwords).verifyAndClear(any(), anyString());
  }
  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(12, TimeUnit.SECONDS))
        throw new IllegalStateException("test coordination timed out");
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("test interrupted");
    }
  }
  private static int status(Runnable operation) {
    try {
      operation.run();
      return 200;
    } catch (ResponseStatusException failure) {
      return failure.getStatusCode().value();
    }
  }
}
