package dev.keynest.manager.notification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.schema.PolicyRegistry;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ExpiryReminderTimingTest {
  private final ExpiryReminderTiming timing = new ExpiryReminderTiming(PolicyRegistry.withDefaults());

  @Test
  void explicitExpiryTakesPrecedenceEvenForUnknownPolicy() {
    OffsetDateTime expiry = OffsetDateTime.parse("2026-11-13T12:00:00Z");
    assertThat(timing.effectiveExpiry(expiry, null, null, "unknown")).isEqualTo(expiry.toInstant());
  }

  @Test
  void fallbackExpiryMovesWhenThePasswordIsRotated() {
    OffsetDateTime created = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    OffsetDateTime rotated = OffsetDateTime.parse("2026-02-01T00:00:00Z");
    assertThat(timing.effectiveExpiry(null, null, created, PasswordPolicy.DEFAULT_POLICY_ID))
      .isEqualTo(created.plusDays(90).toInstant());
    assertThat(timing.effectiveExpiry(null, rotated, created, PasswordPolicy.DEFAULT_POLICY_ID))
      .isEqualTo(rotated.plusDays(90).toInstant());
    assertThat(timing.effectiveExpiry(null, rotated, created, "unknown")).isNull();
  }

  @Test
  void dueWindowIncludesItsBoundaryAndTreatsMissingOrNegativeNoticeAsZero() {
    Instant now = Instant.parse("2026-11-13T12:00:00Z");
    Instant boundary = Instant.parse("2026-11-14T12:00:00Z");
    assertThat(timing.isDue(boundary, 1, now)).isTrue();
    assertThat(timing.isDue(boundary.plusNanos(1), 1, now)).isFalse();
    assertThat(timing.isDue(now, null, now)).isTrue();
    assertThat(timing.isDue(boundary, -1, now)).isFalse();
    assertThat(timing.isDue(null, 1, now)).isFalse();
  }
}
