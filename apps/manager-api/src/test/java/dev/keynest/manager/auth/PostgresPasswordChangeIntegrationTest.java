package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.keynest.manager.security.PasswordHashingService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class PostgresPasswordChangeIntegrationTest {
  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    .withDatabaseName("password_change").withUsername("keynest").withPassword("disposable_test");
  @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }
  @Autowired AuthService auth;
  @Autowired PasswordChangeService changes;
  @Autowired PasswordChangeTransactions changeTransactions;
  @Autowired SessionRevocationService revocations;
  @Autowired UserAccountRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  @MockitoSpyBean PasswordHashingService passwords;
  @MockitoSpyBean AuthSessionRepository sessions;
  private static final String CURRENT = "Synthetic account password!42", NEXT = "Synthetic replacement password!42";
  private AuthService.AuthResponse register() { return auth.register(UUID.randomUUID() + "@example.invalid", CURRENT); }
  private String bearer(AuthService.AuthResponse value) { return "Bearer " + value.token(); }
  private AuthService.AuthResponse change(AuthService.AuthResponse value) { return changes.change(bearer(value), CURRENT.toCharArray(), NEXT.toCharArray()); }

  @Test
  void verifiesAndHashesOutsideMutationTransactionAndRevokesAllPriorOwnerSessions() {
    var first = register();var second = auth.login(first.email(), CURRENT);var foreign = register();
    doAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return call.callRealMethod(); })
      .when(passwords).verifyAndClear(any(), anyString());
    doAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return call.callRealMethod(); })
      .when(passwords).hashAndClear(any());
    var replacement = change(first);
    assertThat(!replacement.token().equals(first.token())).isTrue();
    assertThat(status(() -> auth.requireUser(bearer(first)))).isEqualTo(401);
    assertThat(status(() -> auth.requireUser(bearer(second)))).isEqualTo(401);
    assertThat(auth.requireUser(bearer(replacement)).getId()).isEqualTo(first.userId());
    assertThat(auth.requireUser(bearer(foreign)).getId()).isEqualTo(foreign.userId());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id = ?", Long.class, first.userId())).isEqualTo(1L);
    assertThat(status(() -> auth.login(first.email(), CURRENT))).isEqualTo(401);
    assertThat(auth.login(first.email(), NEXT).userId()).isEqualTo(first.userId());
  }

  @Test
  void revokedSessionAfterHashingCannotChangeThePasswordOrRemoveAnotherSession() throws Exception {
    var first = register();var second = auth.login(first.email(), CURRENT);
    var hashed = new CountDownLatch(1);var release = new CountDownLatch(1);holdHash(hashed, release);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var changing = pool.submit(() -> status(() -> change(first)));
      try { await(hashed);auth.logout(bearer(first)); } finally { release.countDown(); }
      assertThat(changing.get(15, TimeUnit.SECONDS)).isEqualTo(401);
    }
    assertThat(auth.requireUser(bearer(second)).getId()).isEqualTo(first.userId());
    assertThat(auth.login(first.email(), CURRENT).userId()).isEqualTo(first.userId());
  }

  @Test
  void expiryAfterHashingCannotChangeThePassword() throws Exception {
    var first = register();var hashed = new CountDownLatch(1);var release = new CountDownLatch(1);holdHash(hashed, release);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var changing = pool.submit(() -> status(() -> change(first)));
      try { await(hashed);jdbc.update("UPDATE auth_sessions SET expires_at = ? WHERE token_digest = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)), SessionTokenDigest.digest(first.token())); }
      finally { release.countDown(); }
      assertThat(changing.get(15, TimeUnit.SECONDS)).isEqualTo(401);
    }
    assertThat(auth.login(first.email(), CURRENT).userId()).isEqualTo(first.userId());
  }

  @Test
  void changedCredentialAfterProofCannotBeOverwritten() throws Exception {
    var first = register();String changed = passwords.hash("Synthetic competing credential!42");
    var hashed = new CountDownLatch(1);var release = new CountDownLatch(1);holdHash(hashed, release);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var changing = pool.submit(() -> status(() -> change(first)));
      try { await(hashed);jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", changed, first.userId()); }
      finally { release.countDown(); }
      assertThat(changing.get(15, TimeUnit.SECONDS)).isEqualTo(401);
    }
    assertThat(users.findById(first.userId()).orElseThrow().getPasswordHash().equals(changed)).isTrue();
    assertThat(auth.requireUser(bearer(first)).getId()).isEqualTo(first.userId());
  }

  @Test
  void independentPasswordChangesCannotBothCommitFromTheSameOldAuthority() throws Exception {
    var first = register();var second = auth.login(first.email(), CURRENT);
    var independent = new PasswordChangeService(auth, passwords, changeTransactions);
    var hashed = new CountDownLatch(2);var release = new CountDownLatch(1);holdHash(hashed, release);
    var one = new AtomicReference<AuthService.AuthResponse>();var two = new AtomicReference<AuthService.AuthResponse>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> status(() -> one.set(change(first))));
      var b = pool.submit(() -> status(() -> two.set(independent.change(bearer(second), CURRENT.toCharArray(), NEXT.toCharArray()))));
      try { await(hashed); } finally { release.countDown(); }
      assertThat(java.util.List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 401);
    }
    var winner = one.get() == null ? two.get() : one.get();
    assertThat(auth.requireUser(bearer(winner)).getId()).isEqualTo(first.userId());
    assertThat(status(() -> auth.requireUser(bearer(first)))).isEqualTo(401);
    assertThat(status(() -> auth.requireUser(bearer(second)))).isEqualTo(401);
  }

  @Test
  void oldPasswordLoginVerifiedBeforeAChangeCannotIssueAfterIt() throws Exception {
    var first = register();var verified = new CountDownLatch(1);var release = new CountDownLatch(1);
    doAnswer(call -> { boolean valid = (boolean) call.callRealMethod();verified.countDown();await(release);return valid; })
      .when(passwords).verify(eq(CURRENT), anyString());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var login = pool.submit(() -> status(() -> auth.login(first.email(), CURRENT)));
      AuthService.AuthResponse replacement;
      try { await(verified);replacement = change(first); } finally { release.countDown(); }
      assertThat(login.get(15, TimeUnit.SECONDS)).isEqualTo(401);
      assertThat(auth.requireUser(bearer(replacement)).getId()).isEqualTo(first.userId());
    }
  }

  @Test
  void passwordChangeAndRevokeAllShareOneCommittedAuthority() throws Exception {
    var first = register();var second = auth.login(first.email(), CURRENT);
    var verified = new CountDownLatch(2);var release = new CountDownLatch(1);
    doAnswer(call -> { boolean valid = (boolean) call.callRealMethod();verified.countDown();await(release);return valid; })
      .when(passwords).verifyAndClear(any(), anyString());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var changing = pool.submit(() -> status(() -> change(first)));
      var revoking = pool.submit(() -> status(() -> revocations.revokeAll(bearer(second), CURRENT.toCharArray())));
      try { await(verified); } finally { release.countDown(); }
      assertThat(java.util.List.of(changing.get(15, TimeUnit.SECONDS), revoking.get(15, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(200, 401);
    }
    assertThat(status(() -> auth.requireUser(bearer(first)))).isEqualTo(401);
    assertThat(status(() -> auth.requireUser(bearer(second)))).isEqualTo(401);
  }

  @Test
  void mutationWaitsForTheParentAccountLock() throws Exception {
    var first = register();var locked = new CountDownLatch(1);var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
        users.findForPolicyUpdate(first.userId()).orElseThrow();locked.countDown();await(release);return null;
      }));
      try {
        await(locked);var changing = pool.submit(() -> change(first));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);long waiters = 0;
        while (System.nanoTime() < deadline) {
          waiters = jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE '%users%'", Long.class);
          if (waiters > 0) break;Thread.sleep(10);
        }
        assertThat(waiters).as("password change waits for the account lock").isGreaterThan(0);
        release.countDown();holder.get(15, TimeUnit.SECONDS);
        assertThat(auth.requireUser(bearer(changing.get(15, TimeUnit.SECONDS))).getId()).isEqualTo(first.userId());
      } finally { release.countDown(); }
    }
  }

  @Test
  void sessionInsertFailureRollsBackHashAndDeletions() {
    var first = register();var second = auth.login(first.email(), CURRENT);
    String oldHash = users.findById(first.userId()).orElseThrow().getPasswordHash();
    doThrow(new IllegalStateException("synthetic insert failure")).when(sessions).save(any(AuthSession.class));
    assertThatThrownBy(() -> change(first)).isInstanceOf(IllegalStateException.class);
    assertThat(users.findById(first.userId()).orElseThrow().getPasswordHash().equals(oldHash)).isTrue();
    assertThat(auth.requireUser(bearer(first)).getId()).isEqualTo(first.userId());
    assertThat(auth.requireUser(bearer(second)).getId()).isEqualTo(first.userId());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id = ?", Long.class, first.userId())).isEqualTo(2L);
  }

  @Test
  void foreignSessionAndOuterRollbackCannotReplaceTheCredentialOrKeepANewBearer() {
    var first = register();var foreign = register();
    String oldHash = users.findById(first.userId()).orElseThrow().getPasswordHash();String newHash = passwords.hash(NEXT);
    assertThat(status(() -> changeTransactions.replace(first.userId(), oldHash, SessionTokenDigest.digest(foreign.token()), newHash))).isEqualTo(401);
    var rolledBack = new AtomicReference<AuthService.AuthResponse>();
    assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      rolledBack.set(changeTransactions.replace(first.userId(), oldHash, SessionTokenDigest.digest(first.token()), newHash));
      throw new IllegalStateException("synthetic outer rollback");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(users.findById(first.userId()).orElseThrow().getPasswordHash().equals(oldHash)).isTrue();
    assertThat(auth.requireUser(bearer(first)).getId()).isEqualTo(first.userId());
    assertThat(auth.requireUser(bearer(foreign)).getId()).isEqualTo(foreign.userId());
    assertThat(status(() -> auth.requireUser(bearer(rolledBack.get())))).isEqualTo(401);
  }

  private void holdHash(CountDownLatch hashed, CountDownLatch release) {
    doAnswer(call -> { String result = (String) call.callRealMethod();hashed.countDown();await(release);return result; })
      .when(passwords).hashAndClear(any());
  }
  private static void await(CountDownLatch latch) {
    try { if (!latch.await(12, TimeUnit.SECONDS)) throw new IllegalStateException("test coordination timed out"); }
    catch (InterruptedException failure) { Thread.currentThread().interrupt();throw new IllegalStateException("test interrupted"); }
  }
  private static int status(Runnable action) {
    try { action.run();return 200; } catch (ResponseStatusException failure) { return failure.getStatusCode().value(); }
  }
}
