package dev.keynest.manager.notification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ExpiryReminderClaims {
  private final JdbcTemplate jdbc;

  ExpiryReminderClaims(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  boolean claim(Key key, UUID token, Instant now, Instant leaseUntil, int maxAttempts) {
    if (!leaseUntil.isAfter(now) || maxAttempts < 1) {
      throw new IllegalArgumentException("Invalid reminder claim limits");
    }
    return jdbc.update("""
      UPDATE expiry_notification_outbox
      SET status = 'CLAIMED', claim_token = ?, lease_until = ?, attempts = attempts + 1
      WHERE vault_item_id = ? AND expires_at = ? AND attempts < ?
        AND ((status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
          OR (status = 'CLAIMED' AND lease_until <= ?))
      """, token, utc(leaseUntil), key.itemId(), utc(key.expiresAt()), maxAttempts, utc(now), utc(now)) == 1;
  }

  int failExhausted(Instant now, int maxAttempts) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("Invalid reminder attempt limit");
    }
    return jdbc.update("""
      UPDATE expiry_notification_outbox
      SET status = 'FAILED', next_attempt_at = NULL, claim_token = NULL, lease_until = NULL
      WHERE attempts >= ?
        AND ((status = 'CLAIMED' AND lease_until <= ?) OR status = 'PENDING')
      """, maxAttempts, utc(now));
  }

  boolean finish(Key key, UUID token, Instant now, Result result) {
    return transition(key, token, now, result.name(), null);
  }

  boolean retry(Key key, UUID token, Instant now, Instant nextAttemptAt) {
    if (!nextAttemptAt.isAfter(now)) {
      throw new IllegalArgumentException("Retry must be scheduled in the future");
    }
    return transition(key, token, now, "PENDING", utc(nextAttemptAt));
  }

  private boolean transition(Key key, UUID token, Instant now, String status, OffsetDateTime nextAttempt) {
    return jdbc.update("""
      UPDATE expiry_notification_outbox
      SET status = ?, next_attempt_at = ?, claim_token = NULL, lease_until = NULL
      WHERE vault_item_id = ? AND expires_at = ? AND status = 'CLAIMED'
        AND claim_token = ? AND lease_until > ?
      """, status, nextAttempt, key.itemId(), utc(key.expiresAt()), token, utc(now)) == 1;
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  record Key(UUID itemId, Instant expiresAt) {}

  enum Result { SENT, FAILED, CANCELLED }
}
