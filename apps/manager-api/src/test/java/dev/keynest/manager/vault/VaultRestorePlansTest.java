package dev.keynest.manager.vault;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultRestorePlansTest {
  private static VaultRestorePlans.Binding binding() { return new VaultRestorePlans.Binding(UUID.randomUUID(), "synthetic-session-digest", "synthetic-credential-digest"); }
  @Test void bindsOwnerSessionCredentialAndExpiresWithoutRetainingFileData() {
    var clock = new MutableClock();var plans = new VaultRestorePlans(clock);var owner = binding();
    var ticket = plans.issue(owner, new VaultRestoreService.Preview(2, "a".repeat(64), clock.instant(), true));
    String token = ticket.planId();
    assertThat(ticket.planId()).matches("[A-Za-z0-9_-]{43}");
    assertThatThrownBy(() -> plans.consume(token, binding())).isInstanceOf(VaultRestoreFailure.class);
    assertThat(plans.consume(token, owner).itemCount()).isEqualTo(2);
    assertThatThrownBy(() -> plans.consume(token, owner)).isInstanceOf(VaultRestoreFailure.class);
    ticket = plans.issue(owner, new VaultRestoreService.Preview(2, "a".repeat(64), clock.instant(), true));
    String expired = ticket.planId();clock.now = clock.now.plusSeconds(300);
    assertThatThrownBy(() -> plans.consume(expired, owner)).isInstanceOf(VaultRestoreFailure.class);
  }
  @Test void onlyOneConcurrentConsumerWinsAndCapacityIsBounded() throws Exception {
    var clock = new MutableClock();var plans = new VaultRestorePlans(clock);var owner = binding();
    var preview = new VaultRestoreService.Preview(1, "a".repeat(64), clock.instant(), true);
    var ticket = plans.issue(owner, preview);
    try (var executor = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Boolean> consume = () -> { try { plans.consume(ticket.planId(), owner);return true; } catch (VaultRestoreFailure denied) { return false; } };
      var results = executor.invokeAll(java.util.List.of(consume, consume));
      assertThat(results.stream().filter(result -> { try { return result.get(); } catch (Exception failure) { throw new RuntimeException(failure); } }).count()).isEqualTo(1);
    }
    for (int i = 0; i < VaultRestorePlans.MAX_PLANS; i++) plans.issue(owner, preview);
    assertThatThrownBy(() -> plans.issue(owner, preview)).isInstanceOf(VaultRestoreFailure.class);
    clock.now = clock.now.plusSeconds(301);assertThatCode(() -> plans.issue(owner, preview)).doesNotThrowAnyException();
  }
  private static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-23T00:00:00Z");
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }
}
