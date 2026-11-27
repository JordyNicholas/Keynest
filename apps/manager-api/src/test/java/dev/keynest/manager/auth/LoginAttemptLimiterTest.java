package dev.keynest.manager.auth;

import dev.keynest.manager.config.LoginThrottleProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class LoginAttemptLimiterTest {
  private final AtomicLong now = new AtomicLong();

  private LoginAttemptLimiter limiter(int failures, int entries) {
    var settings = new LoginThrottleProperties();settings.setFailureLimit(failures);settings.setMaxEntries(entries);
    return new LoginAttemptLimiter(settings, now::get);
  }

  @Test
  void cooldownStartsAtThresholdAndRejectedRequestsNeverExtendIt() {
    var limiter = limiter(2, 10);
    fail(limiter, "target");fail(limiter, "target");
    assertThat(rejected(limiter, "target").getHeaders().getFirst("Retry-After")).isEqualTo("30");
    now.addAndGet(TimeUnit.SECONDS.toNanos(29));
    assertThat(rejected(limiter, "target").getHeaders().getFirst("Retry-After")).isEqualTo("1");
    now.addAndGet(TimeUnit.SECONDS.toNanos(1));
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
    fail(limiter, "target");
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
  }

  @Test
  void observationWindowExpiresAndSuccessfulAuthenticationClearsHistory() {
    var limiter = limiter(2, 10);
    fail(limiter, "target");now.addAndGet(TimeUnit.SECONDS.toNanos(60));
    fail(limiter, "target");
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
    fail(limiter, "target");
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
  }

  @Test
  void abortedWorkReleasesCapacityWithoutCountingOrErasingPriorFailures() {
    var limiter = limiter(2, 1);
    try (var attempt = limiter.begin("aborted")) { /* Infrastructure exception/cancellation. */ }
    fail(limiter, "target");
    try (var attempt = limiter.begin("target")) { }
    fail(limiter, "target");rejected(limiter, "target");
    rejected(limiter, "other");
    now.addAndGet(TimeUnit.SECONDS.toNanos(30));
    try (var attempt = limiter.begin("other")) { attempt.succeeded(); }
  }

  @Test
  void activeEntriesCannotExpireOrBeEvictedAndCompletionIsIdempotent() {
    var limiter = limiter(1, 1);
    var active = limiter.begin("target");
    now.addAndGet(TimeUnit.HOURS.toNanos(2));
    rejected(limiter, "target");rejected(limiter, "other");
    active.failed();active.succeeded();active.close();
    rejected(limiter, "target");
    now.addAndGet(TimeUnit.SECONDS.toNanos(30));
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
    // A stale completed permit must not remove a later entry for the same key.
    fail(limiter, "target");active.close();rejected(limiter, "target");
  }

  @Test
  void aFullTableFailsClosedThenReclaimsExpiredInactiveEntries() {
    var limiter = limiter(2, 1);
    fail(limiter, "target");
    assertThat(rejected(limiter, "other").getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
    now.addAndGet(TimeUnit.SECONDS.toNanos(60));
    try (var attempt = limiter.begin("other")) { attempt.succeeded(); }
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
  }

  @Test
  void cooldownIsNotBypassedWhenTheObservationWindowExpiresFirst() {
    var settings = new LoginThrottleProperties();settings.setFailureLimit(1);settings.setWindowSeconds(1);
    var limiter = new LoginAttemptLimiter(settings, now::get);
    fail(limiter, "target");now.addAndGet(TimeUnit.SECONDS.toNanos(2));
    rejected(limiter, "target");
    now.addAndGet(TimeUnit.SECONDS.toNanos(28));
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
  }

  @Test
  void onlyOneConcurrentAttemptCanReserveAnIdentifier() throws Exception {
    var limiter = limiter(5, 100);
    var admitted = new AtomicInteger();
    var ready = new CountDownLatch(16);var start = new CountDownLatch(1);
    var denied = new CountDownLatch(15);var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(16)) {
      var tasks = new ArrayList<Future<?>>();
      for (int i = 0; i < 16; i++) tasks.add(pool.submit(() -> {
        ready.countDown();assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try (var attempt = limiter.begin("target")) {
          admitted.incrementAndGet();assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();attempt.failed();
        } catch (ResponseStatusException failure) {
          assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);denied.countDown();
        }
        return null;
      }));
      try {
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();start.countDown();
        assertThat(denied.await(5, TimeUnit.SECONDS)).isTrue();assertThat(admitted.get()).isEqualTo(1);
      } finally { start.countDown();release.countDown(); }
      for (var task : tasks) task.get(5, TimeUnit.SECONDS);
    }
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
  }

  @Test
  void monotonicWrapAndBackwardTestClockDoNotPrematurelyExpireEntries() {
    var limiter = limiter(1, 2);
    now.set(Long.MAX_VALUE - TimeUnit.SECONDS.toNanos(1));fail(limiter, "target");
    now.addAndGet(TimeUnit.SECONDS.toNanos(2));rejected(limiter, "target");
    now.addAndGet(TimeUnit.SECONDS.toNanos(28));
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
    now.set(TimeUnit.SECONDS.toNanos(100));fail(limiter, "target");
    now.set(TimeUnit.SECONDS.toNanos(99));rejected(limiter, "target");
  }

  @Test
  void invalidSettingsAndOversizedIdentifiersAreRefusedWithoutAReservation() {
    List<Consumer<LoginThrottleProperties>> invalid = List.of(
      p -> p.setFailureLimit(0), p -> p.setFailureLimit(101),
      p -> p.setWindowSeconds(0), p -> p.setWindowSeconds(3601),
      p -> p.setCooldownSeconds(0), p -> p.setCooldownSeconds(3601),
      p -> p.setMaxEntries(0), p -> p.setMaxEntries(100001));
    for (var change : invalid) {
      var settings = new LoginThrottleProperties();change.accept(settings);
      assertThatThrownBy(() -> new LoginAttemptLimiter(settings)).isInstanceOf(IllegalArgumentException.class);
    }
    var limiter = limiter(1, 1);
    for (String identifier : new String[] {null, " ", "x".repeat(321)}) {
      assertThatThrownBy(() -> limiter.begin(identifier)).isInstanceOf(IllegalArgumentException.class);
    }
    try (var attempt = limiter.begin("target")) { attempt.succeeded(); }
  }

  private static void fail(LoginAttemptLimiter limiter, String identifier) {
    try (var attempt = limiter.begin(identifier)) { attempt.failed(); }
  }

  private static ResponseStatusException rejected(LoginAttemptLimiter limiter, String identifier) {
    try (var unexpected = limiter.begin(identifier)) { throw new AssertionError("Admission should be refused"); }
    catch (ResponseStatusException failure) {
      assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
      assertThat(failure.getReason()).isEqualTo("Sign-in is temporarily unavailable. Try again later.");
      return failure;
    }
  }
}
