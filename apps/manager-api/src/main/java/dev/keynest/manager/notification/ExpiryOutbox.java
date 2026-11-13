package dev.keynest.manager.notification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ExpiryOutbox {
  private static final int PAGE_SIZE = 250;
  private static final String CANDIDATES = """
    SELECT id, expires_at, last_rotated_at, created_at, updated_at, notify_before_days, policy_id
    FROM vault_items
    """;

  private final JdbcTemplate jdbc;
  private final ExpiryReminderTiming timing;

  ExpiryOutbox(JdbcTemplate jdbc, ExpiryReminderTiming timing) {
    this.jdbc = jdbc;
    this.timing = timing;
  }

  void enqueueDue(Instant now) {
    UUID cursor = null;
    List<Candidate> page;
    do {
      page = pageAfter(cursor);
      for (Candidate candidate : page) {
        enqueueIfDue(candidate, now);
        cursor = candidate.id();
      }
    } while (page.size() == PAGE_SIZE);
  }

  private List<Candidate> pageAfter(UUID cursor) {
    String filter = cursor == null ? "" : "WHERE id > ? ";
    Object[] args = cursor == null ? new Object[] { PAGE_SIZE } : new Object[] { cursor, PAGE_SIZE };
    return jdbc.query(CANDIDATES + filter + "ORDER BY id LIMIT ?", (rs, row) -> new Candidate(
      rs.getObject("id", UUID.class),
      rs.getObject("expires_at", OffsetDateTime.class),
      rs.getObject("last_rotated_at", OffsetDateTime.class),
      rs.getObject("created_at", OffsetDateTime.class),
      rs.getObject("updated_at", OffsetDateTime.class),
      rs.getObject("notify_before_days", Integer.class),
      rs.getString("policy_id")
    ), args);
  }

  private void enqueueIfDue(Candidate item, Instant now) {
    Instant expiry = timing.effectiveExpiry(item.expiry(), item.lastRotatedAt(), item.createdAt(), item.policyId());
    if (!timing.isDue(expiry, item.noticeDays(), now)) {
      return;
    }

    OffsetDateTime due = expiry.atOffset(ZoneOffset.UTC);
    try {
      jdbc.update("""
        INSERT INTO expiry_notification_outbox (vault_item_id, expires_at, queued_at)
        SELECT v.id, ?, ? FROM vault_items v
        WHERE v.id = ? AND v.updated_at = ?
          AND NOT EXISTS (
            SELECT 1 FROM expiry_notification_outbox n
            WHERE n.vault_item_id = v.id AND n.expires_at = ?
          )
        """, due, now.atOffset(ZoneOffset.UTC), item.id(), item.updatedAt(), due);
    } catch (DuplicateKeyException ignored) {
      // A concurrent scanner already enqueued this item's expiry.
    }
  }

  private record Candidate(
    UUID id, OffsetDateTime expiry, OffsetDateTime lastRotatedAt, OffsetDateTime createdAt,
    OffsetDateTime updatedAt, Integer noticeDays, String policyId
  ) {}
}
