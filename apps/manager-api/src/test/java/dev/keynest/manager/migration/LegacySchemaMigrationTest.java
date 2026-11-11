package dev.keynest.manager.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySchemaMigrationTest {

  @Test
  void baselinesLegacySchemaAndPreservesRowsThroughV2() throws Exception {
    String url = "jdbc:h2:mem:legacy-" + UUID.randomUUID()
      + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    String digest = "a".repeat(64);

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      createLegacySchema(connection);
      insertLegacyRows(connection, userId, sessionId, itemId, digest);
    }

    Flyway flyway = Flyway.configure()
      .dataSource(url, "sa", "")
      .locations("classpath:db/migration")
      .baselineOnMigrate(true)
      .baselineVersion("1")
      .validateMigrationNaming(true)
      .load();

    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      assertThat(singleString(connection, "SELECT token_digest FROM auth_sessions WHERE id = '" + sessionId + "'"))
        .isEqualTo(digest);
      assertThat(singleLong(connection, "SELECT COUNT(*) FROM users WHERE id = '" + userId + "'"))
        .isEqualTo(1);
      assertThat(singleLong(connection, "SELECT COUNT(*) FROM vault_items WHERE id = '" + itemId + "'"))
        .isEqualTo(1);
      assertThat(singleLong(connection, """
        SELECT COUNT(*) FROM flyway_schema_history
        WHERE success = TRUE AND version IS NOT NULL
        """))
        .isEqualTo(2);

      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM users WHERE id = '" + userId + "'");
      }
      assertThat(singleLong(connection, "SELECT COUNT(*) FROM auth_sessions WHERE user_id = '" + userId + "'"))
        .isZero();
      assertThat(singleLong(connection, "SELECT COUNT(*) FROM vault_items WHERE user_id = '" + userId + "'"))
        .isZero();
    }
  }

  private static void createLegacySchema(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
        CREATE TABLE users (
          id UUID PRIMARY KEY,
          email VARCHAR(320) NOT NULL UNIQUE,
          password_hash VARCHAR(255) NOT NULL,
          created_at TIMESTAMP WITH TIME ZONE NOT NULL
        )
        """);
      statement.execute("""
        CREATE TABLE auth_sessions (
          id UUID PRIMARY KEY,
          user_id UUID NOT NULL,
          token VARCHAR(64) NOT NULL UNIQUE,
          expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
          created_at TIMESTAMP WITH TIME ZONE NOT NULL
        )
        """);
      statement.execute("""
        CREATE TABLE vault_items (
          id UUID PRIMARY KEY,
          user_id UUID NOT NULL,
          label VARCHAR(200) NOT NULL,
          url VARCHAR(2000),
          username VARCHAR(320),
          envelope_bytes VARBINARY(8192) NOT NULL,
          policy_id VARCHAR(120) NOT NULL,
          aad_version INTEGER,
          expires_at TIMESTAMP WITH TIME ZONE,
          last_rotated_at TIMESTAMP WITH TIME ZONE,
          notify_before_days INTEGER,
          created_at TIMESTAMP WITH TIME ZONE NOT NULL,
          updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        )
        """);
    }
  }

  private static void insertLegacyRows(
    Connection connection,
    UUID userId,
    UUID sessionId,
    UUID itemId,
    String digest
  ) throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    try (PreparedStatement statement = connection.prepareStatement(
      "INSERT INTO users (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)"
    )) {
      statement.setObject(1, userId);
      statement.setString(2, "legacy@example.com");
      statement.setString(3, "legacy-hash");
      statement.setObject(4, now);
      statement.executeUpdate();
    }
    try (PreparedStatement statement = connection.prepareStatement(
      "INSERT INTO auth_sessions (id, user_id, token, expires_at, created_at) VALUES (?, ?, ?, ?, ?)"
    )) {
      statement.setObject(1, sessionId);
      statement.setObject(2, userId);
      statement.setString(3, digest);
      statement.setObject(4, now.plusDays(7));
      statement.setObject(5, now);
      statement.executeUpdate();
    }
    try (PreparedStatement statement = connection.prepareStatement("""
      INSERT INTO vault_items (
        id, user_id, label, envelope_bytes, policy_id, aad_version,
        notify_before_days, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """)) {
      statement.setObject(1, itemId);
      statement.setObject(2, userId);
      statement.setString(3, "Legacy item");
      statement.setBytes(4, new byte[]{1, 2, 3});
      statement.setString(5, "keynest.default.v1");
      statement.setInt(6, 1);
      statement.setInt(7, 14);
      statement.setObject(8, now);
      statement.setObject(9, now);
      statement.executeUpdate();
    }
  }

  private static long singleLong(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static String singleString(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
    }
  }
}
