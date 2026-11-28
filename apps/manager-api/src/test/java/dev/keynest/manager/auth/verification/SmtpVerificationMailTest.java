package dev.keynest.manager.auth.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static dev.keynest.manager.auth.verification.VerificationMail.Result.*;
import static dev.keynest.manager.auth.verification.LoopbackSmtp.Mode.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.angus.mail.util.WriteTimeoutSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SmtpVerificationMailTest {
  @TempDir
  static Path temporary;
  static SSLContext fixtureTls;

  @BeforeAll
  static void generateEphemeralFixtureCertificate() throws Exception {
    String password = UUID.randomUUID().toString();
    Path keys = temporary.resolve("disposable.p12");
    Path tool = Path.of(System.getProperty("java.home"), "bin", "keytool");
    Process process = new ProcessBuilder(tool.toString(), "-genkeypair", "-alias", "smtp", "-keyalg", "RSA", "-keysize",
      "2048", "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-storetype", "PKCS12",
      "-keystore", keys.toString(), "-storepass", password, "-noprompt").redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).isZero();
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (var input = Files.newInputStream(keys)) {
      store.load(input, password.toCharArray());
    }
    var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    km.init(store, password.toCharArray());
    var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tm.init(store);
    fixtureTls = SSLContext.getInstance("TLS");
    fixtureTls.init(km.getKeyManagers(), tm.getTrustManagers(), null);
    Files.delete(keys);
  }

  @Test
  void submitsOneFixedMessageOnlyAfterAuthenticatedRequiredTlsInBothModes() throws Exception {
    for (boolean implicit : new boolean[]{false, true}) {
      try (var sink = new LoopbackSmtp(fixtureTls, implicit, NORMAL);
        var mail = adapter(sink, implicit);
        var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
        assertThat(mail.send(delivery, () -> true)).isEqualTo(SENT);
        assertThat(sink.messages.get()).isEqualTo(1);
        assertThat(sink.authentications.get()).isEqualTo(1);
        assertThat(sink.authenticatedOverTls).isTrue();
        assertThat(sink.expectedEnvelope).isTrue();
        assertThat(sink.protocol).isIn("TLSv1.3", "TLSv1.2");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
          new ByteArrayInputStream(sink.captured.getBytes(StandardCharsets.US_ASCII)));
        assertThat(message.getSubject()).isEqualTo("Confirm your email for Keynest");
        String text = (String) message.getContent();
        delivery.useToken(value -> assertThat(
          text.contains("https://keynest.example.invalid/verify-email#purpose=NEW_ACCOUNT&token=" + new String(value)))
          .as("fixed-origin fragment link").isTrue());
        assertThat(text.contains("Choose your password")).isTrue();
        assertThat(text.contains("sign in normally")).isTrue();
        assertThat(mail.toString()).isEqualTo("SmtpVerificationMail[redacted]");
        assertThat(sink.observedClose()).isTrue();
      }
    }
  }

  @Test
  void legacyTemplateRequiresTheInitiatingSessionAndDoesNotPromiseLogin() throws Exception {
    try (var sink = new LoopbackSmtp(fixtureTls, false, NORMAL);
      var mail = adapter(sink, false);
      var delivery = delivery(VerificationRequest.Purpose.EXISTING_OWNER)) {
      assertThat(mail.send(delivery, () -> true)).isEqualTo(SENT);
      MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
        new ByteArrayInputStream(sink.captured.getBytes(StandardCharsets.US_ASCII)));
      String text = (String) message.getContent();
      assertThat(text.contains("#purpose=EXISTING_OWNER&token=")).isTrue();
      assertThat(text.contains("same Keynest tab and session")).isTrue();
      assertThat(text.contains("cannot sign you in")).isTrue();
    }
  }

  @Test
  void rejectsDowngradeMissingAuthenticationAndRejectedAuthenticationBeforeMessageSubmission() throws Exception {
    for (var mode : new LoopbackSmtp.Mode[]{NO_STARTTLS, REJECT_STARTTLS, NO_AUTH, REJECT_AUTH, LEGACY_TLS}) {
      try (var sink = new LoopbackSmtp(fixtureTls, false, mode);
        var mail = adapter(sink, false);
        var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
        assertThat(mail.send(delivery, () -> true)).isEqualTo(FAILED);
        assertThat(sink.messages.get()).isZero();
        if (mode != REJECT_AUTH)
          assertThat(sink.authentications.get()).isZero();
        else {
          assertThat(sink.authentications.get()).isEqualTo(1);
          assertThat(sink.authenticatedOverTls).isTrue();
        }
        assertThat(sink.connections.get()).isEqualTo(1);
        assertThat(sink.observedClose()).isTrue();
      }
    }
  }

  @Test
  void rejectsUntrustedCertificatesAndHostnameMismatchInBothTlsModes() throws Exception {
    for (boolean implicit : new boolean[]{false, true}) {
      for (boolean wrongHost : new boolean[]{false, true}) {
        try (var sink = new LoopbackSmtp(fixtureTls, implicit, NORMAL);
          var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
          var values = VerificationMailSettingsTest.validSettings(sink.server.getLocalPort(), implicit);
          values.put("host", wrongHost ? "127.0.0.1" : "localhost");
          SSLSocketFactory factory = wrongHost
            ? fixtureTls.getSocketFactory()
            : (SSLSocketFactory) SSLSocketFactory.getDefault();
          try (var mail = new SmtpVerificationMail(VerificationMailSettingsTest.settings(values), factory,
            Duration.ofSeconds(2), Duration.ofSeconds(5))) {
            assertThat(mail.send(delivery, () -> true)).isEqualTo(FAILED);
            assertThat(sink.authentications.get()).isZero();
            assertThat(sink.messages.get()).isZero();
            assertThat(sink.observedClose()).isTrue();
          }
        }
      }
    }
  }

  @Test
  void missingAcknowledgementIsAmbiguousAndNeverRetries() throws Exception {
    try (var sink = new LoopbackSmtp(fixtureTls, false, LOST_ACK);
      var mail = shortAdapter(sink, false, 250, 1500);
      var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
      assertThat(mail.send(delivery, () -> true)).isEqualTo(AMBIGUOUS);
      assertThat(sink.messages.get()).isEqualTo(1);
      assertThat(sink.connections.get()).isEqualTo(1);
      assertThat(sink.observedClose()).isTrue();
    }
  }

  @Test
  void wholeAttemptDeadlineClosesGreetingAndTlsHandshakeSocketsWithoutInterruptOnlyCancellation() throws Exception {
    for (var mode : new LoopbackSmtp.Mode[]{HANG_GREETING, HANG_HANDSHAKE, SLOW_GREETING}) {
      boolean implicit = mode == HANG_HANDSHAKE;
      try (var sink = new LoopbackSmtp(fixtureTls, implicit, mode);
        var mail = shortAdapter(sink, implicit, 2000, 500);
        var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
        long start = System.nanoTime();
        assertThat(mail.send(delivery, () -> true)).isEqualTo(FAILED);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        assertThat(sink.observedClose()).isTrue();
        assertThat(sink.messages.get()).isZero();
        assertThat(sink.authentications.get()).isZero();
      }
    }
  }

  @Test
  void interruptAndShutdownCloseInFlightSocketsAndPreserveCallerInterruption() throws Exception {
    for (boolean interrupt : new boolean[]{true, false}) {
      try (var sink = new LoopbackSmtp(fixtureTls, false, HANG_GREETING);
        var mail = shortAdapter(sink, false, 2000, 5000);
        var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
        var result = new java.util.concurrent.atomic.AtomicReference<VerificationMail.Result>();
        var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        Thread caller = new Thread(() -> {
          result.set(mail.send(delivery, () -> true));
          interrupted.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        try {
          assertThat(sink.accepted.await(3, TimeUnit.SECONDS)).isTrue();
          if (interrupt)
            caller.interrupt();
          else
            mail.close();
          caller.join(3000);
          assertThat(caller.isAlive()).isFalse();
          assertThat(result.get()).isEqualTo(FAILED);
          assertThat(interrupted.get()).isEqualTo(interrupt);
          assertThat(sink.observedClose()).isTrue();
        } finally {
          caller.interrupt();
          caller.join(3000);
        }
      }
    }
  }

  @Test
  void socketReadDeadlineClosesAPeerThatNeverGreets() throws Exception {
    try (var sink = new LoopbackSmtp(fixtureTls, false, HANG_GREETING);
      var mail = shortAdapter(sink, false, 200, 2000);
      var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
      assertThat(mail.send(delivery, () -> true)).isEqualTo(FAILED);
      assertThat(sink.observedClose()).isTrue();
    }
  }

  @Test
  void realBlockedWriteUsesTheSameProviderGuardToCloseItsUnderlyingSocket() throws Exception {
    var timer = new ScheduledThreadPoolExecutor(1);
    timer.setRemoveOnCancelPolicy(true);
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); var raw = new Socket()) {
      server.setReceiveBufferSize(1024);
      raw.setSendBufferSize(1024);
      raw.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()), 1000);
      try (var peer = server.accept(); var guarded = new WriteTimeoutSocket(raw, 200, timer)) {
        peer.setReceiveBufferSize(1024);
        assertThatThrownBy(() -> guarded.getOutputStream().write(new byte[16 * 1024 * 1024]))
          .isInstanceOf(java.io.IOException.class);
        assertThat(raw.isClosed()).isTrue();
      }
    } finally {
      timer.shutdownNow();
    }
  }

  @Test
  void twoWorkersHaveNoQueuedThirdTaskAndShutdownLeavesNoNewWork() throws Exception {
    var settings = VerificationMailSettingsTest.validSettings(1, false);
    CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
    try (
      var mail = new SmtpVerificationMail(VerificationMailSettingsTest.settings(settings),
        fixtureTls.getSocketFactory(), Duration.ofSeconds(1), Duration.ofSeconds(5));
      var executor = Executors.newFixedThreadPool(2);
      var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
      java.util.function.BooleanSupplier blocked = () -> {
        entered.countDown();
        try {
          release.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
        return false;
      };
      var a = executor.submit(() -> mail.send(delivery, blocked));
      var b = executor.submit(() -> mail.send(delivery, blocked));
      try {
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(mail.send(delivery, () -> true)).isEqualTo(UNAVAILABLE);
      } finally {
        release.countDown();
      }
      assertThat(a.get(5, TimeUnit.SECONDS)).isEqualTo(SUPPRESSED);
      assertThat(b.get(5, TimeUnit.SECONDS)).isEqualTo(SUPPRESSED);
      mail.close();
      assertThat(mail.send(delivery, () -> true)).isEqualTo(UNAVAILABLE);
    }
  }

  @Test
  void rechecksEligibilityBeforeDataAndSuppressesClosedMaterialOrMalformedStoredAddresses() throws Exception {
    try (var sink = new LoopbackSmtp(fixtureTls, false, NORMAL);
      var mail = adapter(sink, false);
      var delivery = delivery(VerificationRequest.Purpose.NEW_ACCOUNT)) {
      AtomicInteger checks = new AtomicInteger();
      assertThat(mail.send(delivery, () -> checks.incrementAndGet() == 1)).isEqualTo(SUPPRESSED);
      assertThat(sink.authentications.get()).isEqualTo(1);
      assertThat(sink.messages.get()).isZero();
      delivery.close();
      assertThat(mail.send(delivery, () -> true)).isEqualTo(SUPPRESSED);
      assertThat(mail.send(delivery, () -> {
        throw new IllegalStateException("Private callback data");
      })).isEqualTo(SUPPRESSED);
    }
    try (
      var mail = new SmtpVerificationMail(
        VerificationMailSettingsTest.settings(VerificationMailSettingsTest.validSettings(1, false)));
      var invalid = VerificationDelivery.mint(UUID.randomUUID(), UUID.randomUUID(),
        new VerificationRequest(VerificationRequest.Purpose.NEW_ACCOUNT, "invalid", "invalid", null),
        Instant.now().plusSeconds(600))) {
      assertThat(mail.send(invalid, () -> true)).isEqualTo(FAILED);
      assertThat(mail.send(invalid, () -> {
        throw new IllegalStateException("Private callback data");
      })).isEqualTo(FAILED);
    }
  }

  @Test
  void configurationDoesNotProbeSendOrClaimAndInvalidConfigurationLeavesCoreAvailable() throws Exception {
    try (var sink = new LoopbackSmtp(fixtureTls, false, NORMAL);
      var context = new AnnotationConfigApplicationContext()) {
      Map<String, Object> values = new HashMap<>();
      VerificationMailSettingsTest.validSettings(sink.server.getLocalPort(), false)
        .forEach((key, value) -> values.put(
          ("keynest.verification-mail." + key).replace('.', '_').replace('-', '_').toUpperCase(java.util.Locale.ROOT),
          value));
      context.getEnvironment().getPropertySources()
        .addFirst(new SystemEnvironmentPropertySource("disposable-mail", values));
      context.register(VerificationMailConfiguration.class);
      context.refresh();
      assertThat(context.getBean(VerificationMail.class).readiness()).isEqualTo(VerificationMail.Readiness.READY);
      assertThat(sink.connections.get()).isZero();
    }
    try (var mail = new SmtpVerificationMail(VerificationMailSettings.read(name -> null))) {
      assertThat(mail.readiness()).isEqualTo(VerificationMail.Readiness.UNCONFIGURED);
      assertThat(mail.send(null, () -> true)).isEqualTo(UNAVAILABLE);
      TransactionSynchronizationManager.setActualTransactionActive(true);
      try {
        assertThatThrownBy(() -> mail.send(null, () -> true)).isInstanceOf(IllegalStateException.class);
      } finally {
        TransactionSynchronizationManager.setActualTransactionActive(false);
      }
    }
    try (var mail = new SmtpVerificationMail(VerificationMailSettings.read(name -> "invalid"))) {
      assertThat(mail.readiness()).isEqualTo(VerificationMail.Readiness.INVALID);
      assertThat(mail.send(null, () -> true)).isEqualTo(UNAVAILABLE);
    }
  }

  private SmtpVerificationMail adapter(LoopbackSmtp sink, boolean implicit) {
    return shortAdapter(sink, implicit, 2000, 5000);
  }
  private SmtpVerificationMail shortAdapter(LoopbackSmtp sink, boolean implicit, int socket, int attempt) {
    return new SmtpVerificationMail(
      VerificationMailSettingsTest
        .settings(VerificationMailSettingsTest.validSettings(sink.server.getLocalPort(), implicit)),
      fixtureTls.getSocketFactory(), Duration.ofMillis(socket), Duration.ofMillis(attempt));
  }
  private VerificationDelivery delivery(VerificationRequest.Purpose purpose) {
    var owner = purpose == VerificationRequest.Purpose.EXISTING_OWNER
      ? new VerificationRequest.OwnerBinding(UUID.randomUUID(),
        VerificationDelivery.digestClient(UUID.randomUUID().toString()))
      : null;
    return VerificationDelivery.mint(UUID.randomUUID(), UUID.randomUUID(),
      new VerificationRequest(purpose, "owner+tag@example.invalid", "Owner+Tag@Example.Invalid", owner),
      Instant.now().plusSeconds(600));
  }
}
