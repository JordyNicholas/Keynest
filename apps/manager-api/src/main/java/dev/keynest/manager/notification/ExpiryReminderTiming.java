package dev.keynest.manager.notification;

import dev.keynest.policy.schema.PolicyRegistry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** Applies the same effective-expiry and notice-window rules for scanning and dispatch. */
@Component
class ExpiryReminderTiming {
  private final PolicyRegistry policies;

  ExpiryReminderTiming(PolicyRegistry policies) {
    this.policies = policies;
  }

  Instant effectiveExpiry(OffsetDateTime explicitExpiry, OffsetDateTime lastRotatedAt,
                          OffsetDateTime createdAt, String policyId) {
    if (explicitExpiry != null) {
      return explicitExpiry.toInstant();
    }
    Integer maxAge = policies.find(policyId)
      .map(policy -> policy.toRuntimePolicy().maxAgeDays())
      .orElse(null);
    if (maxAge == null) {
      return null;
    }
    OffsetDateTime passwordSetAt = lastRotatedAt == null ? createdAt : lastRotatedAt;
    return passwordSetAt.toInstant().plus(maxAge, ChronoUnit.DAYS);
  }

  boolean isDue(Instant expiry, Integer noticeDays, Instant now) {
    int leadDays = noticeDays == null ? 0 : Math.max(0, noticeDays);
    return expiry != null && !expiry.isAfter(now.plus(leadDays, ChronoUnit.DAYS));
  }
}
