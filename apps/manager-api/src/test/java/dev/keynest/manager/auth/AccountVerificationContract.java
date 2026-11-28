package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.keynest.manager.auth.verification.VerificationDelivery;
import dev.keynest.manager.auth.verification.VerificationMail;
import dev.keynest.manager.auth.verification.VerificationRequest;
import dev.keynest.manager.auth.verification.VerificationStore;
import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.manager.vault.VaultItemRepository;
import dev.keynest.manager.vault.VaultService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Same application/authority contract on isolated H2 and PostgreSQL; no HTTP or provider traffic. */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
abstract class AccountVerificationContract {
  @Autowired
  AccountVerificationService app;
  @Autowired
  AccountVerificationTransactions transactions;
  @Autowired
  AuthService auth;
  @Autowired
  VerificationStore store;
  @Autowired
  UserAccountRepository users;
  @Autowired
  AuthSessionRepository sessions;
  @Autowired
  SessionRevocationTransactions revocation;
  @Autowired
  PasswordChangeTransactions passwordChange;
  @Autowired
  VaultService vault;
  @Autowired
  VaultItemRepository items;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  PlatformTransactionManager manager;
  @MockitoBean
  VerificationMail mail;
  @MockitoSpyBean
  PasswordHashingService passwords;
  @MockitoSpyBean
  Clock clock;

  @BeforeEach
  void resetOnlyThisIsolatedDatabase() {
    for (String table : new String[]{"verification_delivery_jobs", "verification_challenges", "verification_admissions",
      "verification_client_events", "verification_recipients", "verification_clients", "expiry_notification_outbox",
      "vault_items", "auth_sessions", "users"})
      jdbc.update("DELETE FROM " + table);
    when(mail.readiness()).thenReturn(VerificationMail.Readiness.READY);
  }

  @Test
  void activationCreatesOneVerifiedOwnerWithChosenPasswordAndNoSessionOrAutomaticLogin() {
    String address = signupAddress();
    try (Link link = signup(address)) {
      char[] token = link.copy(), chosen = password().toCharArray();
      String expected = new String(chosen);
      doAnswer(call -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return call.callRealMethod();
      }).when(passwords).hashAndClear(any());
      app.activate(token, chosen);
      assertThat(zero(token) && zero(chosen)).isTrue();
      assertThat(users.count()).isEqualTo(1);
      assertThat(sessions.count()).isZero();
      UserAccount owner = users.findAll().getFirst();
      assertThat(owner.isEmailVerified()).isTrue();
      assertThat(owner.getEmail()).isEqualTo(address.toLowerCase(java.util.Locale.ROOT));
      assertThat(owner.getDeliveryEmail()).isEqualTo(address);
      assertThatThrownBy(() -> app.activate(link.copy(), password().toCharArray()))
        .isInstanceOf(ResponseStatusException.class);
      assertThat(auth.login(address, expected).userId()).isEqualTo(owner.getId());
    }
  }

  @Test
  void badProofWrongPurposeAndBadPasswordsDoNotHashCreateOrConsume() {
    try (Link link = signup(signupAddress())) {
      for (char[] invalid : new char[][]{new char[0], new char[44], "!".repeat(43).toCharArray()}) {
        char[] chosen = password().toCharArray();
        assertThatThrownBy(() -> app.activate(invalid, chosen)).isInstanceOf(ResponseStatusException.class);
        assertThat(zero(invalid) && zero(chosen)).isTrue();
      }
      for (char[] invalid : new char[][]{new char[0], "short".toCharArray(), " ".repeat(16).toCharArray(),
        new char[32769]}) {
        char[] proof = link.copy();
        assertThatThrownBy(() -> app.activate(proof, invalid)).isInstanceOf(ResponseStatusException.class);
        assertThat(zero(proof) && zero(invalid)).isTrue();
      }
      verify(passwords, never()).hashAndClear(any());
      assertThat(store.canConsume(link.copy(), VerificationRequest.Purpose.NEW_ACCOUNT, null)).isTrue();
      assertThat(users.count()).isZero();
    }
  }

  @Test
  void whitespaceAndSupplementaryUnicodePasswordAreUnchangedAtOrdinarySignin() {
    String chosen = "  " + "\uD83D\uDD10".repeat(15) + "  ";
    String address = signupAddress();
    try (Link link = signup(address)) {
      app.activate(link.copy(), chosen.toCharArray());
      UserAccount owner = users.findAll().getFirst();
      assertThat(passwords.verify(chosen, owner.getPasswordHash())).isTrue();
      assertThat(passwords.verify(chosen.trim(), owner.getPasswordHash())).isFalse();
      assertThat(auth.login(address, chosen).userId()).isEqualTo(owner.getId());
    }
  }

  @Test
  void expiredProofAndExpiryDuringHashingCannotCreateAnAccount() {
    Instant start = clock.instant();
    AtomicReference<Instant> now = new AtomicReference<>(start);
    doAnswer(call -> now.get()).when(clock).instant();
    try (Link expired = signup(signupAddress())) {
      now.set(start.plusSeconds(600));
      assertThatThrownBy(() -> app.activate(expired.copy(), password().toCharArray()))
        .isInstanceOf(ResponseStatusException.class);
      verify(passwords, never()).hashAndClear(any());
    }
    now.set(start.plusSeconds(601));
    try (Link during = signup(signupAddress())) {
      doAnswer(call -> {
        Object hash = call.callRealMethod();
        now.set(now.get().plusSeconds(600));
        return hash;
      }).when(passwords).hashAndClear(any());
      assertThatThrownBy(() -> app.activate(during.copy(), password().toCharArray()))
        .isInstanceOf(ResponseStatusException.class);
      assertThat(users.count()).isZero();
    }
  }

  @Test
  void rejectedResendPreservesProofAndAcceptedResendDuringHashingRevokesIt() throws Exception {
    for (boolean replace : new boolean[]{false, true}) {
      String address = signupAddress();
      Instant start = clock.instant();
      doReturn(start).when(clock).instant();
      try (Link link = signup(address); var executor = Executors.newSingleThreadExecutor()) {
        var hashed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        holdHash(hashed, release);
        var activation = executor.submit(() -> outcome(() -> app.activate(link.copy(), password().toCharArray())));
        try {
          await(hashed);
          if (replace)
            doReturn(start.plusSeconds(60)).when(clock).instant();
          var mailbox = dev.keynest.manager.auth.verification.VerificationMailbox.parse(address);
          var admission = store.admit(new VerificationRequest(VerificationRequest.Purpose.NEW_ACCOUNT,
            mailbox.identity(), mailbox.delivery(), null), "resend-client");
          assertThat(admission)
            .isEqualTo(replace ? VerificationStore.Admission.ACCEPTED : VerificationStore.Admission.RECIPIENT_LIMIT);
        } finally {
          release.countDown();
        }
        assertThat(activation.get(10, TimeUnit.SECONDS)).isEqualTo(replace ? 400 : 204);
      }
    }
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void twoApplicationReplicasCannotActivateTheSameProofTwice() throws Exception {
    try (Link link = signup(signupAddress()); var executor = Executors.newFixedThreadPool(2)) {
      var other = new AccountVerificationService(auth, passwords, transactions, store, mail);
      var hashed = new CountDownLatch(2);
      var release = new CountDownLatch(1);
      holdHash(hashed, release);
      String first = password(), second = password();
      var a = executor.submit(() -> outcome(() -> app.activate(link.copy(), first.toCharArray())));
      var b = executor.submit(() -> outcome(() -> other.activate(link.copy(), second.toCharArray())));
      try {
        await(hashed);
      } finally {
        release.countDown();
      }
      assertThat((a.get(10, TimeUnit.SECONDS) == 204 ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) == 204 ? 1 : 0))
        .isEqualTo(1);
      assertThat(users.count()).isEqualTo(1);
      assertThat(sessions.count()).isZero();
      String hash = users.findAll().getFirst().getPasswordHash();
      assertThat(passwords.verify(first, hash) ^ passwords.verify(second, hash)).isTrue();
    }
  }

  @Test
  void hashingAndInsertFailureLeaveProofUnconsumedAndExposeOnlyFixedErrors(
    org.springframework.boot.test.system.CapturedOutput output) {
    String address = signupAddress();
    try (Link link = signup(address)) {
      doThrow(new IllegalStateException("Private password work detail")).when(passwords).hashAndClear(any());
      char[] proof = link.copy(), chosen = password().toCharArray();
      assertThatThrownBy(() -> app.activate(proof, chosen)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
        assertThat(e.getReason()).isEqualTo("verification unavailable");
        assertThat(e.getCause()).isNull();
      });
      assertThat(zero(proof) && zero(chosen)).isTrue();
      assertThat(store.canConsume(link.copy(), VerificationRequest.Purpose.NEW_ACCOUNT, null)).isTrue();
      AtomicReference<String> generatedHash = new AtomicReference<>();
      doAnswer(call -> {
        Object hash = call.callRealMethod();
        generatedHash.set((String) hash);
        return hash;
      }).when(passwords).hashAndClear(any());
      // Stored canonical identity is lowercase; fail all new verified writes in this isolated database.
      jdbc.execute("ALTER TABLE users ADD CONSTRAINT synthetic_verified_failure CHECK (email_verified_at IS NULL)");
      try {
        assertThatThrownBy(() -> app.activate(link.copy(), password().toCharArray()))
          .isInstanceOf(ResponseStatusException.class);
        assertThat(users.count()).isZero();
        assertThat(store.canConsume(link.copy(), VerificationRequest.Purpose.NEW_ACCOUNT, null)).isTrue();
        assertThat(output.getAll().contains(address.toLowerCase(java.util.Locale.ROOT)))
          .as("no address in ORM failure logs").isFalse();
        assertThat(generatedHash.get() != null && !output.getAll().contains(generatedHash.get()))
          .as("no password hash in ORM failure logs").isTrue();
      } finally {
        jdbc.execute("ALTER TABLE users DROP CONSTRAINT synthetic_verified_failure");
      }
    }
  }

  @Test
  void postCommitFailureIsUncertainWithoutRetryOrPasswordReset() {
    try (Link link = signup(signupAddress())) {
      var uncertain = mock(AccountVerificationTransactions.class);
      doAnswer(call -> {
        transactions.activate(call.getArgument(0), call.getArgument(1));
        throw new TransactionSystemException("Private commit acknowledgement detail");
      }).when(uncertain).activate(any(), any());
      var service = new AccountVerificationService(auth, passwords, uncertain, store, mail);
      String chosen = password();
      char[] proof = link.copy(), input = chosen.toCharArray();
      assertThatThrownBy(() -> service.activate(proof, input)).isInstanceOfSatisfying(ResponseStatusException.class,
        e -> {
          assertThat(e.getReason()).isEqualTo("verification unavailable");
          assertThat(e.getCause()).isNull();
        });
      assertThat(zero(proof) && zero(input)).isTrue();
      assertThat(users.count()).isEqualTo(1);
      assertThat(sessions.count()).isZero();
      assertThat(store.canConsume(link.copy(), VerificationRequest.Purpose.NEW_ACCOUNT, null)).isFalse();
      assertThatThrownBy(() -> app.activate(link.copy(), password().toCharArray()))
        .isInstanceOf(ResponseStatusException.class);
      UserAccount owner = users.findAll().getFirst();
      assertThat(auth.login(owner.getEmail(), chosen).userId()).isEqualTo(owner.getId());
    }
  }

  @Test
  void legacyRequestRequiresFreshPasswordAndConfirmationRequiresTheInitiatingOwnerSession() {
    var owner = legacy(null);
    var other = legacy(null);
    String beforeHash = users.findById(owner.userId()).orElseThrow().getPasswordHash();
    var current = auth.requireUser(owner.token());
    var item = vault.create(current, new VaultService.CreateVaultItemRequest("Verification preservation",
      "https://example.invalid", "owner", null, true, null, 90, 14));
    byte[] envelope = items.findByIdAndUserId(item.id(), owner.userId()).orElseThrow().getEnvelopeBytes().clone();
    char[] wrong = "wrong".toCharArray();
    assertThat(outcome(() -> app.requestOwner(owner.token(), wrong, "client-owner"))).isEqualTo(403);
    assertThat(zero(wrong)).isTrue();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenges", Integer.class)).isZero();
    assertThat(app.requestOwner(owner.token(), "old".toCharArray(), "client-owner"))
      .isEqualTo(VerificationStore.Admission.ACCEPTED);
    try (Link proof = claim()) {
      assertThat(outcome(() -> app.activate(proof.copy(), password().toCharArray()))).isEqualTo(400);
      assertThat(outcome(() -> app.confirmOwner(other.token(), proof.copy()))).isEqualTo(400);
      var anotherSession = auth.login(owner.email(), "old");
      assertThat(outcome(() -> app.confirmOwner(anotherSession.token(), proof.copy()))).isEqualTo(400);
      app.confirmOwner(owner.token(), proof.copy());
      var verified = auth.requireUser(owner.token());
      assertThat(verified.isEmailVerified()).isTrue();
      assertThat(verified.getPasswordHash().equals(beforeHash)).isTrue();
      assertThat(
        Arrays.equals(items.findByIdAndUserId(item.id(), owner.userId()).orElseThrow().getEnvelopeBytes(), envelope))
        .isTrue();
      assertThat(auth.requireUser(anotherSession.token()).getId()).isEqualTo(owner.userId());
      assertThat(outcome(() -> app.confirmOwner(owner.token(), proof.copy()))).isEqualTo(400);
    }
  }

  @Test
  void failedLegacyUpdateRestoresProofAndPreservesUnverifiedOwnerState() {
    var owner = legacy(null);
    app.requestOwner(owner.token(), "old".toCharArray(), "client-owner");
    try (Link link = claim()) {
      jdbc.execute("ALTER TABLE users ADD CONSTRAINT synthetic_confirmation_failure CHECK (email_verified_at IS NULL)");
      char[] proof = link.copy();
      try {
        assertThat(outcome(() -> app.confirmOwner(owner.token(), proof))).isEqualTo(503);
        assertThat(zero(proof)).isTrue();
        assertThat(auth.requireUser(owner.token()).isEmailVerified()).isFalse();
        assertThat(store.canConsume(link.copy(), VerificationRequest.Purpose.EXISTING_OWNER,
          new VerificationRequest.OwnerBinding(owner.userId(), SessionTokenDigest.digest(owner.token())))).isTrue();
      } finally {
        jdbc.execute("ALTER TABLE users DROP CONSTRAINT synthetic_confirmation_failure");
      }
      app.confirmOwner(owner.token(), link.copy());
      assertThat(app.requestOwner(owner.token(), "old".toCharArray(), "client-owner"))
        .isEqualTo(VerificationStore.Admission.INVALID_BINDING);
    }
  }

  @Test
  void lateAccountCreationCannotAttachTheSignupPasswordToAnExistingOwner() throws Exception {
    String address = signupAddress();
    try (Link link = signup(address); var executor = Executors.newSingleThreadExecutor()) {
      var hashed = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      holdHash(hashed, release);
      var activation = executor.submit(() -> outcome(() -> app.activate(link.copy(), password().toCharArray())));
      String retainedHash = passwords.hash(password());
      UUID existing = UUID.randomUUID();
      try {
        await(hashed);
        users.saveAndFlush(UserAccount.verified(existing, address.toLowerCase(java.util.Locale.ROOT), address,
          retainedHash, clock.instant()));
      } finally {
        release.countDown();
      }
      assertThat(activation.get(10, TimeUnit.SECONDS)).isEqualTo(400);
      assertThat(users.count()).isEqualTo(1);
      assertThat(sessions.count()).isZero();
      assertThat(users.findById(existing).orElseThrow().getPasswordHash().equals(retainedHash)).isTrue();
    }
  }

  @Test
  void processPasswordAdmissionRejectsCompetingWorkWithoutConsumingProofOrInputs() throws Exception {
    try (Link first = signup(signupAddress());
      Link second = signup(signupAddress());
      var executor = Executors.newSingleThreadExecutor()) {
      var hashed = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      holdHash(hashed, release);
      var activation = executor.submit(() -> outcome(() -> app.activate(first.copy(), password().toCharArray())));
      char[] proof = second.copy(), chosen = password().toCharArray();
      try {
        await(hashed);
        assertThat(outcome(() -> app.activate(proof, chosen))).isEqualTo(429);
        assertThat(zero(proof) && zero(chosen)).isTrue();
        assertThat(store.canConsume(second.copy(), VerificationRequest.Purpose.NEW_ACCOUNT, null)).isTrue();
      } finally {
        release.countDown();
      }
      assertThat(activation.get(10, TimeUnit.SECONDS)).isEqualTo(204);
    }
  }

  @Test
  void mailOutageDoesNotInvalidateAlreadyIssuedProofOrExistingOwnerAccess() {
    var owner = legacy(null);
    assertThat(app.requestOwner(owner.token(), "old".toCharArray(), "client-owner"))
      .isEqualTo(VerificationStore.Admission.ACCEPTED);
    try (Link ownerProof = claim(); Link signup = signup(signupAddress())) {
      when(mail.readiness()).thenReturn(VerificationMail.Readiness.UNCONFIGURED);
      assertThat(outcome(() -> app.requestSignup(signupAddress(), "client-public"))).isEqualTo(503);
      assertThat(outcome(() -> app.requestOwner(owner.token(), "old".toCharArray(), "client-owner"))).isEqualTo(503);
      assertThat(auth.requireUser(owner.token()).isEmailVerified()).isFalse();
      app.confirmOwner(owner.token(), ownerProof.copy());
      app.activate(signup.copy(), password().toCharArray());
      assertThat(users.count()).isEqualTo(2);
    }
  }

  @Test
  void unsupportedLegacyAddressRetainsPasswordAndSessionAccessWithoutSilentCorrection() {
    var owner = legacy("é-" + UUID.randomUUID() + "@example.invalid");
    assertThat(outcome(() -> app.requestOwner(owner.token(), "old".toCharArray(), "client-owner"))).isEqualTo(422);
    assertThat(auth.requireUser(owner.token()).isEmailVerified()).isFalse();
    assertThat(auth.login(owner.email(), "old").userId()).isEqualTo(owner.userId());
    assertThat(users.findById(owner.userId()).orElseThrow().getEmail()).isEqualTo(owner.email());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenges", Integer.class)).isZero();
  }

  @Test
  void ownerRequestRefreshesHashAndSessionAfterPasswordProofAgainstAllRevokingWriters() throws Exception {
    for (int writer = 0; writer < 3; writer++) {
      var owner = legacy(null);
      var current = users.findById(owner.userId()).orElseThrow();
      var verified = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      doAnswer(call -> {
        Object result = call.callRealMethod();
        verified.countDown();
        await(release);
        return result;
      }).when(passwords).verifyAndClear(any(), any());
      try (var executor = Executors.newSingleThreadExecutor()) {
        var request = executor
          .submit(() -> outcome(() -> app.requestOwner(owner.token(), "old".toCharArray(), "client-owner")));
        try {
          await(verified);
          if (writer == 0)
            auth.logout(owner.token());
          else if (writer == 1)
            revocation.revoke(owner.userId(), current.getPasswordHash(), SessionTokenDigest.digest(owner.token()));
          else
            passwordChange.replace(owner.userId(), current.getPasswordHash(), SessionTokenDigest.digest(owner.token()),
              passwords.hash(password()));
        } finally {
          release.countDown();
        }
        assertThat(request.get(10, TimeUnit.SECONDS)).isEqualTo(401);
      }
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenges", Integer.class)).isZero();
  }

  @Test
  void revocationOrderedBeforeConfirmationDeniesItAndConfirmationOrderedFirstCanCommit() throws Exception {
    for (boolean confirmFirst : new boolean[]{false, true}) {
      var owner = legacy(null);
      app.requestOwner(owner.token(), "old".toCharArray(), "client-owner");
      try (Link link = claim(); var executor = Executors.newFixedThreadPool(2)) {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
          users.findForPolicyUpdate(owner.userId()).orElseThrow();
          if (confirmFirst)
            transactions.confirmOwner(owner.userId(), SessionTokenDigest.digest(owner.token()), link.copy());
          else
            auth.logout(owner.token());
          locked.countDown();
          await(release);
          return true;
        }));
        try {
          await(locked);
          var next = executor.submit(() -> confirmFirst
            ? outcome(() -> auth.logout(owner.token()))
            : outcome(() -> app.confirmOwner(owner.token(), link.copy())));
          release.countDown();
          assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
          assertThat(next.get(10, TimeUnit.SECONDS)).isEqualTo(confirmFirst ? 204 : 401);
        } finally {
          release.countDown();
        }
        assertThat(users.findById(owner.userId()).orElseThrow().isEmailVerified()).isEqualTo(confirmFirst);
        assertThat(sessions.findByTokenDigest(SessionTokenDigest.digest(owner.token()))).isEmpty();
      }
    }
  }

  @Test
  void independentUseCaseGuardClearsInputsAndNeverSpansPasswordWorkWithOuterTransactions() {
    try (Link link = signup(signupAddress())) {
      char[] token = link.copy(), chosen = password().toCharArray();
      assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> {
        app.activate(token, chosen);
        return null;
      })).isInstanceOf(ResponseStatusException.class);
      assertThat(zero(token) && zero(chosen)).isTrue();
      verify(passwords, never()).hashAndClear(any());
      assertThat(store.canConsume(link.copy(), VerificationRequest.Purpose.NEW_ACCOUNT, null)).isTrue();
    }
  }

  private AuthService.AuthResponse legacy(String address) {
    UUID id = UUID.randomUUID();
    String email = address == null ? "legacy-" + id + "@example.invalid" : address;
    jdbc.update("INSERT INTO users (id,email,password_hash,created_at,legacy_access_granted) VALUES (?,?,?,?,TRUE)", id,
      email, passwords.hash("old"), Instant.now().atOffset(ZoneOffset.UTC));
    return auth.login(email, "old");
  }
  private Link signup(String address) {
    assertThat(app.requestSignup(address, "client-public")).isEqualTo(VerificationStore.Admission.ACCEPTED);
    return claim();
  }
  private Link claim() {
    return new Link(store.claimNext().orElseThrow());
  }
  private String signupAddress() {
    return "Signup+" + UUID.randomUUID() + "@Example.Invalid";
  }
  private String password() {
    return UUID.randomUUID() + " Aa1!";
  }
  private void holdHash(CountDownLatch hashed, CountDownLatch release) {
    doAnswer(call -> {
      Object result = call.callRealMethod();
      hashed.countDown();
      await(release);
      return result;
    }).when(passwords).hashAndClear(any());
  }
  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Test interrupted");
    }
  }
  private static int outcome(Runnable action) {
    try {
      action.run();
      return 204;
    } catch (ResponseStatusException failure) {
      return failure.getStatusCode().value();
    }
  }
  private static boolean zero(char[] value) {
    for (char c : value)
      if (c != 0)
        return false;
    return true;
  }
  private static final class Link implements AutoCloseable {
    private final VerificationDelivery delivery;
    private final char[] value;
    Link(VerificationDelivery delivery) {
      this.delivery = delivery;
      AtomicReference<char[]> copy = new AtomicReference<>();
      delivery.useToken(token -> copy.set(token.clone()));
      value = copy.get();
    }
    char[] copy() {
      return value.clone();
    }
    public void close() {
      Arrays.fill(value, '\0');
      delivery.close();
    }
    public String toString() {
      return "Link[redacted]";
    }
  }
}
