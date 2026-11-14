package dev.keynest.manager.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class ExpiryReminderDispatcher {
  private static final int MAX_ATTEMPTS = 3;
  private static final int BATCH_SIZE = 50;
  private static final Duration LEASE = Duration.ofMinutes(1);

  private final JdbcTemplate jdbc;
  private final ExpiryReminderClaims claims;
  private final ExpiryReminderTiming timing;
  private final Clock clock;
  private final Delivery delivery;

  ExpiryReminderDispatcher(JdbcTemplate jdbc, ExpiryReminderClaims claims,
                           ExpiryReminderTiming timing, Clock clock, Delivery delivery) {
    this.jdbc = jdbc;
    this.claims = claims;
    this.timing = timing;
    this.clock = clock;
    this.delivery = delivery;
  }

  void dispatchBatch() {
    OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
    claims.failExhausted(now.toInstant(), MAX_ATTEMPTS);
    var candidates = jdbc.query("""
      SELECT vault_item_id, expires_at
      FROM expiry_notification_outbox
      WHERE attempts < ?
        AND ((status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
          OR (status = 'CLAIMED' AND lease_until <= ?))
      ORDER BY queued_at, vault_item_id, expires_at
      LIMIT ?
      """, (rs, row) -> new ExpiryReminderClaims.Key(rs.getObject(1, UUID.class),
          rs.getObject(2, OffsetDateTime.class).toInstant()),
      MAX_ATTEMPTS, now, now, BATCH_SIZE);
    for (var key : candidates) {
      dispatch(key);
    }
  }

  private void dispatch(ExpiryReminderClaims.Key key) {
    UUID token = UUID.randomUUID();
    Instant now = clock.instant();
    if (!claims.claim(key, token, now, now.plus(LEASE), MAX_ATTEMPTS)) {
      return;
    }

    var current = jdbc.query("""
      SELECT v.user_id, v.expires_at, v.last_rotated_at, v.created_at, v.policy_id,
             v.notify_before_days, n.attempts
      FROM expiry_notification_outbox n JOIN vault_items v ON v.id = n.vault_item_id
      WHERE n.vault_item_id = ? AND n.expires_at = ? AND n.claim_token = ? AND n.lease_until > ?
      """, (rs, row) -> new CurrentReminder(
        rs.getObject(1, UUID.class),
        timing.effectiveExpiry(rs.getObject(2, OffsetDateTime.class),
          rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class), rs.getString(5)),
        rs.getObject(6, Integer.class), rs.getInt(7)),
      key.itemId(), key.expiresAt().atOffset(ZoneOffset.UTC), token, clock.instant().atOffset(ZoneOffset.UTC));
    if (current.isEmpty()) {
      return;
    }

    CurrentReminder reminder = current.getFirst();
    if (!key.expiresAt().equals(reminder.expiry())
        || !timing.isDue(reminder.expiry(), reminder.noticeDays(), clock.instant())) {
      claims.finish(key, token, clock.instant(), ExpiryReminderClaims.Result.CANCELLED);
      return;
    }

    Outcome outcome = delivery.deliver(reminder.ownerId());
    Instant finishedAt = clock.instant();
    switch (outcome) {
      case SENT -> claims.finish(key, token, finishedAt, ExpiryReminderClaims.Result.SENT);
      case INELIGIBLE -> claims.finish(key, token, finishedAt, ExpiryReminderClaims.Result.CANCELLED);
      case PERMANENT_FAILURE -> claims.finish(key, token, finishedAt, ExpiryReminderClaims.Result.FAILED);
      case RETRYABLE_FAILURE -> {
        if (reminder.attempts() >= MAX_ATTEMPTS) {
          claims.finish(key, token, finishedAt, ExpiryReminderClaims.Result.FAILED);
        } else {
          claims.retry(key, token, finishedAt, finishedAt.plus(Duration.ofMinutes(1L << reminder.attempts())));
        }
      }
    }
  }

  /** Implementations resolve eligible recipients and bound the full delivery call below the lease duration. */
  interface Delivery {
    Outcome deliver(UUID ownerId);
  }

  enum Outcome { SENT, INELIGIBLE, PERMANENT_FAILURE, RETRYABLE_FAILURE }

  private record CurrentReminder(UUID ownerId, Instant expiry, Integer noticeDays, int attempts) {}
}
