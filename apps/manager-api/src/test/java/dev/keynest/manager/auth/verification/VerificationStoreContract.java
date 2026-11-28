package dev.keynest.manager.auth.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.auth.UserAccountRepository;
import dev.keynest.manager.auth.verification.VerificationRequest.OwnerBinding;
import dev.keynest.manager.auth.verification.VerificationRequest.Purpose;
import dev.keynest.manager.auth.verification.VerificationStore.Admission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

/** The same state/transaction/replica contract runs on H2 and real PostgreSQL. No mail is sent. */
abstract class VerificationStoreContract {
  @Autowired
  DataSource source;
  @Autowired
  PlatformTransactionManager manager;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  UserAccountRepository users;
  MutableClock clock;
  VerificationStore store;

  @BeforeEach
  void resetOnlyThisIsolatedVerificationDatabase() {
    for (String table : new String[]{"verification_delivery_jobs", "verification_challenges", "verification_admissions",
      "verification_client_events", "verification_recipients", "verification_clients"})
      jdbc.update("DELETE FROM " + table);
    clock = new MutableClock();
    store = replica(VerificationStore.Limits.DEFAULT);
  }

  @Test
  void coordinatorDoesNotClaimWithoutMailAndRecordsEachOneAttemptOutcomeWithoutReplay() {
    var mail = org.mockito.Mockito.mock(VerificationMail.class);
    var dispatcher = new VerificationDispatcher(store, mail);
    store.admit(signup(), "client-a");
    org.mockito.Mockito.when(mail.readiness()).thenReturn(VerificationMail.Readiness.INVALID);
    assertThat(dispatcher.dispatchOne()).isEqualTo(VerificationDispatcher.Outcome.UNAVAILABLE);
    assertThat(
      jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenges WHERE token_digest IS NOT NULL", Integer.class))
      .isZero();
    for (var result : VerificationMail.Result.values()) {
      org.mockito.Mockito.when(mail.readiness()).thenReturn(VerificationMail.Readiness.READY);
      AtomicReference<VerificationDelivery> material = new AtomicReference<>();
      org.mockito.Mockito.doAnswer(call -> {
        assertThat(
          org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
          .isFalse();
        assertThat(((java.util.function.BooleanSupplier) call.getArgument(1)).getAsBoolean()).isTrue();
        material.set(call.getArgument(0));
        return result;
      }).when(mail).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
      var expected = result == VerificationMail.Result.SENT
        ? VerificationDispatcher.Outcome.SENT
        : result == VerificationMail.Result.AMBIGUOUS
          ? VerificationDispatcher.Outcome.AMBIGUOUS
          : VerificationDispatcher.Outcome.FAILED;
      assertThat(dispatcher.dispatchOne()).isEqualTo(expected);
      assertThat(material.get().closed()).isTrue();
      assertThat(dispatcher.dispatchOne()).isEqualTo(VerificationDispatcher.Outcome.EMPTY);
      org.mockito.Mockito.verify(mail).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
      org.mockito.Mockito.clearInvocations(mail);
      if (result != VerificationMail.Result.SUPPRESSED)
        store.admit(signup(), "client-" + result);
    }
  }

  @Test
  void coordinatorTreatsThrownSendAsAmbiguousAndStaleResendAsTerminal() {
    var mail = org.mockito.Mockito.mock(VerificationMail.class);
    org.mockito.Mockito.when(mail.readiness()).thenReturn(VerificationMail.Readiness.READY);
    var dispatcher = new VerificationDispatcher(store, mail);
    store.admit(signup(), "client-a");
    org.mockito.Mockito.when(mail.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenThrow(new IllegalStateException("Private provider acknowledgement detail"));
    assertThat(dispatcher.dispatchOne()).isEqualTo(VerificationDispatcher.Outcome.AMBIGUOUS);
    assertThat(dispatcher.dispatchOne()).isEqualTo(VerificationDispatcher.Outcome.EMPTY);
    var request = signup();
    store.admit(request, "client-b");
    org.mockito.Mockito.doAnswer(call -> {
      clock.advance(60);
      store.admit(request, "client-c");
      return VerificationMail.Result.SENT;
    }).when(mail).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    assertThat(dispatcher.dispatchOne()).isEqualTo(VerificationDispatcher.Outcome.STALE);
    try (var next = store.claimNext().orElseThrow()) {
      assertThat(next.request()).isEqualTo(request);
    }
  }

  @Test
  void coordinatorFailsClosedForUnavailableStateAndRefusesOwningTransactions() {
    var mail = org.mockito.Mockito.mock(VerificationMail.class);
    org.mockito.Mockito.when(mail.readiness()).thenReturn(VerificationMail.Readiness.READY);
    var dispatcher = new VerificationDispatcher(store, mail);
    assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> dispatcher.dispatchOne()))
      .isInstanceOf(IllegalStateException.class);
    jdbc.update("DELETE FROM verification_gate");
    try {
      assertThat(dispatcher.dispatchOne()).isEqualTo(VerificationDispatcher.Outcome.UNAVAILABLE);
      org.mockito.Mockito.verify(mail, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
    } finally {
      jdbc.update("INSERT INTO verification_gate (id) VALUES (1)");
    }
  }

  @Test
  void supportedMailboxKeysAgreeWithDatabaseComparisonAndCanonicalUniqueness() {
    for (String input : new String[]{"Owner.Name+Tag@Example.Invalid", "\"Local Part\"@Example.Invalid",
      "\"Quoted@Part\"@Example.Invalid", "I.i@Example.Invalid"}) {
      var address = VerificationMailbox.parse(input);
      assertThat(jdbc.queryForObject("SELECT LOWER(CAST(? AS VARCHAR))", String.class, address.delivery()))
        .isEqualTo(address.identity());
      UUID id = UUID.randomUUID();
      users.saveAndFlush(
        UserAccount.verified(id, address.identity(), address.delivery(), "synthetic-test-hash", clock.instant()));
      try {
        assertThat(users.findByEmailIgnoreCase(address.delivery()).map(UserAccount::getId)).contains(id);
        assertThatThrownBy(() -> users.saveAndFlush(UserAccount.verified(UUID.randomUUID(), address.identity(),
          address.delivery(), "synthetic-test-hash", clock.instant())))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      } finally {
        users.deleteById(id);
      }
    }
  }

  @Test
  void recipientSlidingWindowsAreSharedAndRejectedResendsDoNotReplaceOrExtend() {
    var request = signup();
    assertThat(store.admit(request, "client-a")).isEqualTo(Admission.ACCEPTED);
    UUID original = generation(request);
    clock.advance(59);
    assertThat(replica(VerificationStore.Limits.DEFAULT).admit(request, "client-b"))
      .isEqualTo(Admission.RECIPIENT_LIMIT);
    assertThat(generation(request)).isEqualTo(original);
    clock.advance(1);
    assertThat(store.admit(request, "client-a")).isEqualTo(Admission.ACCEPTED);
    clock.advance(60);
    assertThat(store.admit(request, "client-a")).isEqualTo(Admission.ACCEPTED);
    UUID last = generation(request);
    clock.advance(60);
    assertThat(store.admit(request, "client-a")).isEqualTo(Admission.RECIPIENT_LIMIT);
    assertThat(generation(request)).isEqualTo(last);
    clock.advance(3420);
    assertThat(store.admit(request, "client-a")).isEqualTo(Admission.ACCEPTED);
  }

  @Test
  void clientGlobalAndCapacityRefusalsAreBoundedAndRecover() {
    var request = signup();
    store.admit(request, "client-a");
    for (int i = 1; i < 10; i++)
      assertThat(store.admit(request, "client-a")).isEqualTo(Admission.RECIPIENT_LIMIT);
    assertThat(store.admit(signup(), "client-a")).isEqualTo(Admission.CLIENT_LIMIT);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_client_events", Integer.class)).isEqualTo(10);
    clock.advance(60);
    assertThat(store.admit(request, "client-a")).isEqualTo(Admission.ACCEPTED);
  }

  @Test
  void installationAdmissionAndMetadataCapsApplyAcrossIndependentInstances() {
    var limited = replica(new VerificationStore.Limits(1000, 1000, 3, 1, 10, 2));
    assertThat(limited.admit(signup(), "client-a")).isEqualTo(Admission.ACCEPTED);
    assertThat(replica(new VerificationStore.Limits(1000, 1000, 3, 1, 10, 2)).admit(signup(), "client-b"))
      .isEqualTo(Admission.GLOBAL_LIMIT);
    clock.advance(3600);
    assertThat(limited.admit(signup(), "client-a")).isEqualTo(Admission.ACCEPTED);
  }

  @Test
  void fullCapacityRefusesEvenAResendWithoutRevokingTheExistingGeneration() {
    var limited = replica(new VerificationStore.Limits(2, 1000, 3, 100, 10, 2));
    var request = signup();
    limited.admit(request, "client-a");
    UUID generation = generation(request);
    clock.advance(60);
    assertThat(limited.admit(request, "client-a")).isEqualTo(Admission.CAPACITY);
    assertThat(limited.admit(signup(), "client-b")).isEqualTo(Admission.CAPACITY);
    assertThat(generation(request)).isEqualTo(generation);
    clock.advance(3600);
    assertThat(limited.admit(signup(), "client-a")).isEqualTo(Admission.ACCEPTED);
  }

  @Test
  void clientKeyCapacityIsGlobalAndNeverGrowsOnRefusal() {
    var limited = replica(new VerificationStore.Limits(1000, 2, 3, 100, 10, 2));
    var request = signup();
    limited.admit(request, "client-a");
    limited.admit(request, "client-b");
    assertThat(limited.admit(signup(), "client-c")).isEqualTo(Admission.CAPACITY);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_clients", Integer.class)).isEqualTo(2);
    clock.advance(60);
    assertThat(limited.admit(signup(), "client-c")).isEqualTo(Admission.ACCEPTED);
  }

  @Test
  void concurrentReplicaAdmissionsAndClaimsHaveOneWinner() throws Exception {
    var request = signup();
    var other = replica(VerificationStore.Limits.DEFAULT);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> {
        start.await();
        return store.admit(request, "client-a");
      });
      var b = executor.submit(() -> {
        start.await();
        return other.admit(request, "client-b");
      });
      start.countDown();
      assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(Admission.ACCEPTED, Admission.RECIPIENT_LIMIT);
      var c = executor.submit(store::claimNext);
      var d = executor.submit(other::claimNext);
      var first = c.get(10, TimeUnit.SECONDS);
      var second = d.get(10, TimeUnit.SECONDS);
      assertThat((first.isPresent() ? 1 : 0) + (second.isPresent() ? 1 : 0)).isEqualTo(1);
      first.ifPresent(VerificationDelivery::close);
      second.ifPresent(VerificationDelivery::close);
    }
  }

  @Test
  void digestOnlyTokenIsRedactedSingleUseAndCallbackCopiesAreCleared() {
    var request = signup();
    store.admit(request, "client-a");
    try (var delivery = store.claimNext().orElseThrow()) {
      AtomicReference<char[]> seen = new AtomicReference<>();
      delivery.useToken(seen::set);
      assertThat(allZero(seen.get())).isTrue();
      assertThat(delivery.toString()).isEqualTo("VerificationDelivery[redacted]");
      assertThat(delivery.request().toString()).isEqualTo("VerificationRequest[redacted]");
      String digest = jdbc.queryForObject("SELECT token_digest FROM verification_challenges", String.class);
      assertThat(digest != null && digest.length() == 64).as("only a digest is persisted").isTrue();
      assertThat(store.canDeliver(delivery)).isTrue();
      char[] token = token(delivery);
      assertThat(consume(token, Purpose.EXISTING_OWNER, owner().owner())).isEmpty();
      assertThat(allZero(token)).isTrue();
      assertThat(consume(token(delivery), Purpose.NEW_ACCOUNT, null)).contains(request);
      assertThat(consume(token(delivery), Purpose.NEW_ACCOUNT, null)).isEmpty();
      assertThat(store.canDeliver(delivery)).isFalse();
    }
  }

  @Test
  void consumeRequiresTheAccountTransactionAndRollsBackWithJpaWrites() {
    var request = signup();
    store.admit(request, "client-a");
    try (var delivery = store.claimNext().orElseThrow()) {
      char[] token = token(delivery);
      assertThatThrownBy(() -> store.consume(token, Purpose.NEW_ACCOUNT, null))
        .isInstanceOf(IllegalStateException.class);
      assertThat(allZero(token)).isTrue();
      UUID id = UUID.randomUUID();
      assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> {
        assertThat(store.consume(token(delivery), Purpose.NEW_ACCOUNT, null)).isPresent();
        users.saveAndFlush(UserAccount.verified(id, request.recipient(), request.deliveryAddress(),
          "synthetic-test-hash", clock.instant()));
        throw new IllegalStateException("Synthetic account transaction failure");
      })).isInstanceOf(IllegalStateException.class);
      assertThat(users.existsById(id)).isFalse();
      assertThat(consume(token(delivery), Purpose.NEW_ACCOUNT, null)).isPresent();
    }
  }

  @Test
  void consumptionRacesAreSingleUseAcrossReplicas() throws Exception {
    store.admit(signup(), "client-a");
    try (var delivery = store.claimNext().orElseThrow(); var executor = Executors.newFixedThreadPool(2)) {
      var other = replica(VerificationStore.Limits.DEFAULT);
      var start = new CountDownLatch(1);
      var a = executor.submit(() -> {
        start.await();
        return consume(token(delivery), Purpose.NEW_ACCOUNT, null);
      });
      var b = executor.submit(() -> {
        start.await();
        return new TransactionTemplate(manager)
          .execute(status -> other.consume(token(delivery), Purpose.NEW_ACCOUNT, null));
      });
      start.countDown();
      assertThat((a.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0) + (b.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0))
        .isEqualTo(1);
    }
  }

  @Test
  void acceptedResendInvalidatesOldWorkButRefusedResendDoesNot() {
    var request = signup();
    store.admit(request, "client-a");
    try (var old = store.claimNext().orElseThrow()) {
      assertThat(store.admit(request, "client-b")).isEqualTo(Admission.RECIPIENT_LIMIT);
      assertThat(store.canDeliver(old)).isTrue();
      clock.advance(60);
      assertThat(store.admit(request, "client-a")).isEqualTo(Admission.ACCEPTED);
      assertThat(store.canDeliver(old)).isFalse();
      assertThat(store.finish(old, VerificationStore.DeliveryResult.SENT)).isFalse();
      assertThat(consume(token(old), Purpose.NEW_ACCOUNT, null)).isEmpty();
      try (var replacement = store.claimNext().orElseThrow()) {
        assertThat(consume(token(replacement), Purpose.NEW_ACCOUNT, null)).contains(request);
      }
    }
  }

  @Test
  void leaseExpiryIsNotARetryAndDoesNotInvalidateAnAmbiguouslyDeliveredLink() {
    store.admit(signup(), "client-a");
    store.admit(signup(), "client-b");
    store.admit(signup(), "client-c");
    try (var first = store.claimNext().orElseThrow(); var second = store.claimNext().orElseThrow()) {
      assertThat(store.claimNext()).isEmpty();
      clock.advance(30);
      assertThat(store.canDeliver(first)).isFalse();
      assertThat(store.finish(first, VerificationStore.DeliveryResult.FAILED)).isFalse();
      try (var third = replica(VerificationStore.Limits.DEFAULT).claimNext().orElseThrow()) {
        assertThat(store.claimNext()).isEmpty();
        assertThat(consume(token(first), Purpose.NEW_ACCOUNT, null)).isPresent();
      }
    }
  }

  @Test
  void failedOrAmbiguousSendNeverRequeuesAndTokenExpiryIsIndependentOfCleanup() {
    for (var outcome : VerificationStore.DeliveryResult.values()) {
      store.admit(signup(), "client-" + outcome);
      try (var delivery = store.claimNext().orElseThrow()) {
        assertThat(store.finish(delivery, outcome)).isTrue();
        assertThat(store.finish(delivery, outcome)).isFalse();
        assertThat(replica(VerificationStore.Limits.DEFAULT).claimNext()).isEmpty();
        clock.advance(600);
        assertThat(consume(token(delivery), Purpose.NEW_ACCOUNT, null)).isEmpty();
      }
    }
  }

  @Test
  void queuedExpiryAndKnownAccountSuppressionMintNoToken() {
    var request = signup();
    store.admit(request, "client-a");
    clock.advance(300);
    assertThat(store.claimNext()).isEmpty();
    var known = signup();
    store.admit(known, "client-b");
    users.saveAndFlush(UserAccount.verified(UUID.randomUUID(), known.recipient(), known.deliveryAddress(),
      "synthetic-test-hash", clock.instant()));
    assertThat(store.claimNext()).isEmpty();
    assertThat(
      jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenges WHERE token_digest IS NOT NULL", Integer.class))
      .isZero();
  }

  @Test
  void lateAccountCreationCannotTurnASignupLinkIntoExistingAccountAuthority() {
    var request = signup();
    store.admit(request, "client-a");
    try (var delivery = store.claimNext().orElseThrow()) {
      users.saveAndFlush(UserAccount.verified(UUID.randomUUID(), request.recipient(), request.deliveryAddress(),
        "synthetic-test-hash", clock.instant()));
      assertThat(store.canDeliver(delivery)).isFalse();
      assertThat(consume(token(delivery), Purpose.NEW_ACCOUNT, null)).isEmpty();
    }
  }

  @Test
  void legacyOwnerSessionAddressAndPurposeAreBoundAndRevocationCascades() {
    var request = owner();
    assertThat(store.admit(request, "client-a")).isEqualTo(Admission.ACCEPTED);
    try (var delivery = store.claimNext().orElseThrow()) {
      assertThat(consume(token(delivery), Purpose.EXISTING_OWNER, owner().owner())).isEmpty();
      assertThat(consume(token(delivery), Purpose.NEW_ACCOUNT, null)).isEmpty();
      jdbc.update("DELETE FROM auth_sessions WHERE token_digest = ?", request.owner().sessionDigest());
      assertThat(store.canDeliver(delivery)).isFalse();
      assertThat(consume(token(delivery), Purpose.EXISTING_OWNER, request.owner())).isEmpty();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_admissions", Integer.class)).isEqualTo(1);
    }
  }

  @Test
  void changedOwnerAddressAndExpiredSessionCannotConfirm() {
    var request = owner();
    store.admit(request, "client-a");
    try (var delivery = store.claimNext().orElseThrow()) {
      jdbc.update("UPDATE users SET email = ? WHERE id = ?", "changed-" + UUID.randomUUID() + "@example.invalid",
        request.owner().ownerId());
      assertThat(store.canDeliver(delivery)).isFalse();
      assertThat(consume(token(delivery), Purpose.EXISTING_OWNER, request.owner())).isEmpty();
    }
    var other = owner();
    store.admit(other, "client-b");
    try (var delivery = store.claimNext().orElseThrow()) {
      jdbc.update("UPDATE auth_sessions SET expires_at = ? WHERE token_digest = ?",
        clock.instant().atOffset(ZoneOffset.UTC), other.owner().sessionDigest());
      assertThat(consume(token(delivery), Purpose.EXISTING_OWNER, other.owner())).isEmpty();
    }
  }

  @Test
  void publicPurposeResendsCannotRevokeAnOwnerLinkAndQuotasRemainCombined() {
    var request = owner();
    store.admit(request, "client-a");
    try (var delivery = store.claimNext().orElseThrow()) {
      var publicRequest = new VerificationRequest(Purpose.NEW_ACCOUNT, request.recipient(), request.deliveryAddress(),
        null);
      clock.advance(60);
      assertThat(store.admit(publicRequest, "client-b")).isEqualTo(Admission.ACCEPTED);
      clock.advance(60);
      assertThat(store.admit(publicRequest, "client-b")).isEqualTo(Admission.ACCEPTED);
      clock.advance(60);
      assertThat(store.admit(request, "client-a")).isEqualTo(Admission.RECIPIENT_LIMIT);
      assertThat(consume(token(delivery), Purpose.EXISTING_OWNER, request.owner())).contains(request);
    }
  }

  @Test
  void internalClaimCannotLeakUncommittedMaterialOrDatabaseFailureDetails() {
    store.admit(signup(), "client-a");
    assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> store.claimNext()))
      .isInstanceOf(IllegalStateException.class);
    var uncertain = new PlatformTransactionManager() {
      public TransactionStatus getTransaction(TransactionDefinition definition) {
        return manager.getTransaction(definition);
      }
      public void rollback(TransactionStatus status) {
        manager.rollback(status);
      }
      public void commit(TransactionStatus status) {
        manager.commit(status);
        throw new TransactionSystemException("Private driver details must not escape");
      }
    };
    var failing = new VerificationStore(source, uncertain, clock);
    assertThatThrownBy(failing::claimNext).isInstanceOfSatisfying(VerificationStore.Unavailable.class, failure -> {
      assertThat(failure.getCause()).isNull();
      assertThat(failure.getMessage()).isEqualTo("Verification state unavailable");
    });
    clock.advance(30);
    assertThat(store.claimNext()).isEmpty();
  }

  @Test
  void admissionFailureRollsBackQuotaAndRecipientWrites() {
    jdbc
      .execute("ALTER TABLE verification_challenges ADD CONSTRAINT synthetic_failure CHECK (purpose <> 'NEW_ACCOUNT')");
    try {
      assertThatThrownBy(() -> store.admit(signup(), "client-a")).isInstanceOf(VerificationStore.Unavailable.class);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_recipients", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_admissions", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_client_events", Integer.class)).isZero();
    } finally {
      jdbc.execute("ALTER TABLE verification_challenges DROP CONSTRAINT synthetic_failure");
    }
  }

  @Test
  void cleanupIsBoundedAndAdmissionExpiryDoesNotDependOnItsSchedule() {
    for (int i = 0; i < 105; i++) {
      String key = VerificationDelivery.digestClient("seed-" + i);
      jdbc.update("INSERT INTO verification_clients (client_key, created_at) VALUES (?, ?)", key,
        clock.instant().atOffset(ZoneOffset.UTC));
      jdbc.update("INSERT INTO verification_client_events (id, client_key, accepted_at) VALUES (?, ?, ?)",
        UUID.randomUUID(), key, clock.instant().atOffset(ZoneOffset.UTC));
    }
    clock.advance(60);
    store.cleanBatch();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_clients", Integer.class)).isEqualTo(5);
    store.cleanBatch();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_clients", Integer.class)).isZero();
  }

  @Test
  void inputBoundsRejectControlsWrongBindingsAndOversizeWithoutAllocatingState() {
    var valid = signup();
    for (String client : new String[]{"", " ", "x".repeat(129), "client\r\n"}) {
      assertThatThrownBy(() -> store.admit(valid, client)).isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> new VerificationRequest(Purpose.NEW_ACCOUNT, "x", "x\r\n", null))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VerificationRequest(Purpose.NEW_ACCOUNT, "x".repeat(321), "x".repeat(321), null))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VerificationRequest(Purpose.EXISTING_OWNER, "x", "x", null))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VerificationRequest(Purpose.NEW_ACCOUNT, "x", "X", owner().owner()))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OwnerBinding(UUID.randomUUID(), "not-a-session-digest"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_recipients", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_client_events", Integer.class)).isZero();
  }

  @Test
  void malformedProofsAreClearedAndSecretCopiesClearEvenWhenTheConsumerFails() {
    store.admit(signup(), "client-a");
    try (var delivery = store.claimNext().orElseThrow()) {
      for (char[] bad : new char[][]{new char[1], "!".repeat(43).toCharArray(), new char[128]}) {
        assertThat(consume(bad, Purpose.NEW_ACCOUNT, null)).isEmpty();
        assertThat(allZero(bad)).isTrue();
      }
      AtomicReference<char[]> seen = new AtomicReference<>();
      assertThatThrownBy(() -> delivery.useToken(value -> {
        seen.set(value);
        throw new IllegalStateException("Synthetic consumer failure");
      })).isInstanceOf(IllegalStateException.class);
      assertThat(allZero(seen.get())).isTrue();
      assertThat(consume(token(delivery), Purpose.NEW_ACCOUNT, null)).isPresent();
      delivery.close();
      assertThatThrownBy(() -> delivery.useToken(value -> {
      })).isInstanceOf(IllegalStateException.class);
      assertThat(store.canDeliver(delivery)).isFalse();
    }
  }

  @Test
  void incompatibleOrReadOnlyOwningTransactionsFailClosed() {
    var request = signup();
    var repeatable = new TransactionTemplate(manager);
    repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    assertThatThrownBy(() -> repeatable.execute(status -> store.admit(request, "client-a")))
      .isInstanceOf(VerificationStore.Unavailable.class);
    var readOnly = new TransactionTemplate(manager);
    readOnly.setReadOnly(true);
    assertThatThrownBy(() -> readOnly.execute(status -> store.admit(request, "client-a")))
      .isInstanceOf(VerificationStore.Unavailable.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_admissions", Integer.class)).isZero();
  }

  private VerificationStore replica(VerificationStore.Limits limits) {
    return new VerificationStore(source, manager, clock, limits);
  }
  private VerificationRequest signup() {
    String email = "new-" + UUID.randomUUID() + "@example.invalid";
    return new VerificationRequest(Purpose.NEW_ACCOUNT, email, email, null);
  }
  private VerificationRequest owner() {
    UUID id = UUID.randomUUID();
    String email = "owner-" + id + "@example.invalid";
    String digest = VerificationDelivery.digestClient(UUID.randomUUID().toString());
    jdbc.update("INSERT INTO users (id,email,password_hash,created_at,legacy_access_granted) VALUES (?,?,?,?,TRUE)", id,
      email, "synthetic-test-hash", clock.instant().atOffset(ZoneOffset.UTC));
    jdbc.update("INSERT INTO auth_sessions (id,user_id,token_digest,created_at,expires_at) VALUES (?,?,?,?,?)",
      UUID.randomUUID(), id, digest, clock.instant().atOffset(ZoneOffset.UTC),
      clock.instant().plusSeconds(3600).atOffset(ZoneOffset.UTC));
    return new VerificationRequest(Purpose.EXISTING_OWNER, email, email, new OwnerBinding(id, digest));
  }
  private UUID generation(VerificationRequest request) {
    return jdbc.queryForObject("SELECT id FROM verification_challenges WHERE recipient_key = ? AND purpose = ?",
      UUID.class, request.recipient(), request.purpose().name());
  }
  private java.util.Optional<VerificationRequest> consume(char[] token, Purpose purpose, OwnerBinding owner) {
    return new TransactionTemplate(manager).execute(status -> store.consume(token, purpose, owner));
  }
  private static char[] token(VerificationDelivery delivery) {
    AtomicReference<char[]> copy = new AtomicReference<>();
    delivery.useToken(value -> copy.set(value.clone()));
    return copy.get();
  }
  private static boolean allZero(char[] value) {
    for (char c : value)
      if (c != '\0')
        return false;
    return true;
  }
  static class MutableClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now().truncatedTo(ChronoUnit.MICROS));
    void advance(long seconds) {
      now.updateAndGet(value -> value.plusSeconds(seconds));
    }
    public Instant instant() {
      return now.get();
    }
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now.get(), zone);
    }
  }
}
