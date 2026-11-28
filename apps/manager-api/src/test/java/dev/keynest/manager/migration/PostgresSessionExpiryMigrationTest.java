package dev.keynest.manager.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class PostgresSessionExpiryMigrationTest {
  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    .withDatabaseName("session_upgrade").withUsername("keynest").withPassword("disposable_test");

  @Test
  void upgradesPopulatedV6WithoutRewritingSessionsOrVaultRows() throws Exception {
    Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("6")
      .load().migrate();
    UUID owner = UUID.randomUUID();
    UUID session = UUID.randomUUID();
    UUID item = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    String digest = "a".repeat(64);
    byte[] envelope = new byte[]{1, 2, 3};
    try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
      postgres.getPassword())) {
      try (var statement = connection
        .prepareStatement("INSERT INTO users (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)")) {
        statement.setObject(1, owner);
        statement.setString(2, "upgrade@example.com");
        statement.setString(3, "synthetic-unused-hash");
        statement.setObject(4, now);
        statement.executeUpdate();
      }
      try (var statement = connection.prepareStatement(
        "INSERT INTO auth_sessions (id, user_id, token_digest, expires_at, created_at) VALUES (?, ?, ?, ?, ?)")) {
        statement.setObject(1, session);
        statement.setObject(2, owner);
        statement.setString(3, digest);
        statement.setObject(4, now.plusDays(7));
        statement.setObject(5, now);
        statement.executeUpdate();
      }
      try (var statement = connection.prepareStatement("""
        INSERT INTO vault_items (id, user_id, label, envelope_bytes, policy_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """)) {
        statement.setObject(1, item);
        statement.setObject(2, owner);
        statement.setString(3, "synthetic upgrade item");
        statement.setBytes(4, envelope);
        statement.setString(5, "keynest.default.v1");
        statement.setObject(6, now);
        statement.setObject(7, now);
        statement.executeUpdate();
      }
      var latest = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .load();
      assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);
      assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("8");
      latest.validate();
      try (var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT token_digest, expires_at FROM auth_sessions")) {
        assertThat(rows.next()).isTrue();
        assertThat(digest.equals(rows.getString(1))).as("session digest preserved").isTrue();
        assertThat(rows.getObject(2, OffsetDateTime.class).toInstant())
          .isEqualTo(now.plusDays(7).toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(rows.next()).isFalse();
      }
      try (var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT envelope_bytes FROM vault_items")) {
        assertThat(rows.next()).isTrue();
        assertThat(java.util.Arrays.equals(envelope, rows.getBytes(1))).as("envelope bytes preserved").isTrue();
      }
      try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
        SELECT indexdef FROM pg_indexes WHERE tablename = 'auth_sessions' AND indexname = 'idx_auth_sessions_expiry_id'
        """)) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).contains("(expires_at, id)");
      }
    }
  }
}
