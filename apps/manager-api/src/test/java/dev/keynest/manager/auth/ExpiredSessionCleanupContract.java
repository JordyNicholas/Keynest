package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Same repository/transaction contract on H2 and actual PostgreSQL. */
abstract class ExpiredSessionCleanupContract {
  static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  @TestConfiguration(proxyBeanMethods = false)
  static class TimeConfiguration {
    @Bean
    @Primary
    Clock cleanupTestClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired
  ExpiredSessionCleanup cleanup;
  @Autowired
  AuthService auth;
  @Autowired
  AuthSessionRepository sessions;
  @Autowired
  UserAccountRepository users;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  PlatformTransactionManager transactions;
  private UUID owner;

  @BeforeEach
  void prepareIsolatedDatabase() {
    sessions.deleteAll();
    users.deleteAll();
    owner = UUID.randomUUID();
    users.save(
      dev.keynest.manager.testing.TestAccounts.account(owner, owner + "@example.com", "synthetic-unused-hash", NOW));
  }

  @Test
  void inclusiveExpiryAndRepeatedBoundedProgressPreserveActiveSessionsAndOwners() {
    UUID oldest = add(NOW.minusSeconds(2));
    UUID next = add(NOW.minusSeconds(1));
    UUID boundary = add(NOW);
    UUID active = add(NOW.plusSeconds(1));
    assertThat(sessions.findExpiredIds(NOW, PageRequest.of(0, 2))).containsExactly(oldest, next);
    assertThat(cleanup.cleanBatch()).isEqualTo(2);
    assertThat(sessions.existsById(boundary)).isTrue();
    assertThat(cleanup.cleanBatch()).isEqualTo(1);
    assertThat(cleanup.cleanBatch()).isZero();
    assertThat(sessions.findAll()).extracting(AuthSession::getId).containsExactly(active);
    assertThat(users.existsById(owner)).isTrue();
  }

  @Test
  void authenticationRejectsExpiredRowsBeforeCleanupAndRetainsActiveBearerAfterIt() {
    String expired = UUID.randomUUID().toString();
    String active = UUID.randomUUID().toString();
    // Authentication intentionally retains its real server clock, separate from this fixed cleanup cutoff.
    sessions.save(new AuthSession(UUID.randomUUID(), owner, SessionTokenDigest.digest(expired), NOW.minusSeconds(1),
      NOW.minusSeconds(60)));
    sessions.save(new AuthSession(UUID.randomUUID(), owner, SessionTokenDigest.digest(active),
      Instant.now().plusSeconds(3600), NOW));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> auth.requireUser("Bearer " + expired))
      .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(auth.requireUser("Bearer " + active).getId()).isEqualTo(owner);
    assertThat(cleanup.cleanBatch()).isEqualTo(1);
    assertThat(auth.requireUser("Bearer " + active).getId()).isEqualTo(owner);
  }

  @Test
  void conditionalDeletePreservesSessionExtendedAfterSelectionAndIgnoresMissingIds() {
    UUID extended = add(NOW);
    UUID removed = add(NOW.minusSeconds(1));
    List<UUID> ids = sessions.findExpiredIds(NOW, PageRequest.of(0, 2));
    jdbc.update("UPDATE auth_sessions SET expires_at = ? WHERE id = ?", java.sql.Timestamp.from(NOW.plusSeconds(60)),
      extended);
    sessions.deleteById(removed);
    int deleted = new TransactionTemplate(transactions).execute(status -> sessions.deleteExpiredIds(ids, NOW));
    assertThat(deleted).isZero();
    assertThat(sessions.existsById(extended)).isTrue();
  }

  @Test
  void independentConcurrentTransactionsDeleteEachCandidateOnlyOnce() throws Exception {
    add(NOW);
    add(NOW.minusSeconds(1));
    UUID active = add(NOW.plusSeconds(1));
    List<UUID> ids = sessions.findExpiredIds(NOW, PageRequest.of(0, 2));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> deleteAfter(start, ids));
      var second = executor.submit(() -> deleteAfter(start, ids));
      start.countDown();
      assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(2);
    }
    assertThat(sessions.findAll()).extracting(AuthSession::getId).containsExactly(active);
  }

  private int deleteAfter(CountDownLatch start, List<UUID> ids) throws InterruptedException {
    if (!start.await(10, TimeUnit.SECONDS))
      throw new IllegalStateException("cleanup test did not start");
    return new TransactionTemplate(transactions).execute(status -> sessions.deleteExpiredIds(ids, NOW));
  }

  private UUID add(Instant expiry) {
    UUID id = UUID.randomUUID();
    sessions.save(new AuthSession(id, owner, SessionTokenDigest.digest(UUID.randomUUID().toString()), expiry,
      NOW.minusSeconds(60)));
    return id;
  }
}
