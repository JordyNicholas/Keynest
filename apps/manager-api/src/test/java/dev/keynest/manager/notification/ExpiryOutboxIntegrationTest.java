package dev.keynest.manager.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:expiry-outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ExpiryOutboxIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
  private static final OffsetDateTime OLD = OffsetDateTime.parse("2026-08-01T12:00:00Z");

  @Autowired JdbcTemplate jdbc;
  @Autowired ExpiryOutbox outbox;

  @Test
  void queuesDueItemsOnceAndRemovesRowsWhenTheirVaultItemIsDeleted() {
    UUID user = UUID.randomUUID();
    jdbc.update("INSERT INTO users (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)",
      user, user + "@example.test", "test-hash", OLD);

    UUID due = insertItem(user, OffsetDateTime.parse("2026-11-19T12:00:00Z"), null);
    UUID fallback = insertItem(user, null, null);
    insertItem(user, OffsetDateTime.parse("2026-11-20T12:00:00Z"), null);
    insertItem(user, null, OffsetDateTime.parse("2026-11-10T12:00:00Z"));

    outbox.enqueueDue(NOW);
    outbox.enqueueDue(NOW);

    assertThat(jdbc.query("SELECT vault_item_id FROM expiry_notification_outbox WHERE status = 'PENDING'",
      (rs, row) -> rs.getObject(1, UUID.class))).containsExactlyInAnyOrder(due, fallback);
    jdbc.update("DELETE FROM vault_items WHERE id = ?", due);
    assertThat(jdbc.query("SELECT vault_item_id FROM expiry_notification_outbox",
      (rs, row) -> rs.getObject(1, UUID.class))).containsExactly(fallback);
  }

  private UUID insertItem(UUID user, OffsetDateTime expiry, OffsetDateTime lastRotated) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
      INSERT INTO vault_items (
        id, user_id, label, envelope_bytes, policy_id, expires_at, last_rotated_at,
        notify_before_days, created_at, updated_at
      ) VALUES (?, ?, 'Example', ?, 'keynest.default.v1', ?, ?, 7, ?, ?)
      """, id, user, new byte[] { 1 }, expiry, lastRotated, OLD, OLD);
    return id;
  }
}
