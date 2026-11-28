package dev.keynest.manager.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.keynest.manager.ManagerApiApplication;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.vault.VaultService;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Executes the same clean-install, populated-upgrade and retained-authority contract on both databases. */
abstract class AccountEligibilityMigrationContract {
  abstract String url();
  abstract String username();
  abstract String password();

  @Test
  void cleanInstallRefusesUnverifiedInsertsAndUpgradePreservesOnlyExistingOwners() throws Exception {
    var latest = Flyway.configure().dataSource(url(), username(), password()).cleanDisabled(false).load();
    latest.migrate();
    try (var db = DriverManager.getConnection(url(), username(), password()); var sql = db.createStatement()) {
      assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO users (id,email,password_hash,created_at) VALUES ('"
        + UUID.randomUUID() + "','unverified@example.invalid','synthetic-hash',CURRENT_TIMESTAMP)"))
        .isInstanceOf(SQLException.class);
    }
    // This contract owns an isolated H2 database or Testcontainers database only.
    latest.clean();
    Flyway.configure().dataSource(url(), username(), password()).target("7").load().migrate();
    UUID owner = UUID.randomUUID(), item = UUID.randomUUID();
    String email = "LegacyOwner@example.invalid", candidate = "short";
    String hash = new PasswordHasher().hashPassword(candidate.toCharArray());
    String bearer = UUID.randomUUID().toString(), digest = HexFormat.of()
      .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bearer.getBytes(StandardCharsets.UTF_8)));
    String policy = "keynest.default.v1", secret = "Synthetic!Retained47%Cedar";
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    byte[] envelope = new KeynestSeal()
      .seal(secret.getBytes(StandardCharsets.UTF_8), key, "upgrade-v1", policy.getBytes(StandardCharsets.UTF_8))
      .toBytes();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    try (var db = DriverManager.getConnection(url(), username(), password())) {
      try (var sql = db.prepareStatement("INSERT INTO users (id,email,password_hash,created_at) VALUES (?,?,?,?)")) {
        sql.setObject(1, owner);
        sql.setString(2, email);
        sql.setString(3, hash);
        sql.setObject(4, now);
        sql.executeUpdate();
      }
      try (var sql = db.prepareStatement(
        "INSERT INTO auth_sessions (id,user_id,token_digest,expires_at,created_at) VALUES (?,?,?,?,?)")) {
        sql.setObject(1, UUID.randomUUID());
        sql.setObject(2, owner);
        sql.setString(3, digest);
        sql.setObject(4, now.plusHours(1));
        sql.setObject(5, now);
        sql.executeUpdate();
      }
      try (var sql = db.prepareStatement(
        "INSERT INTO vault_items (id,user_id,label,envelope_bytes,policy_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?)")) {
        sql.setObject(1, item);
        sql.setObject(2, owner);
        sql.setString(3, "Synthetic retained item");
        sql.setBytes(4, envelope);
        sql.setString(5, policy);
        sql.setObject(6, now);
        sql.setObject(7, now);
        sql.executeUpdate();
      }
      assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
      latest.validate();
      try (var sql = db.createStatement(); var rows = sql.executeQuery("SELECT * FROM users")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getBoolean("legacy_access_granted")).isTrue();
        assertThat(rows.getObject("email_verified_at")).isNull();
        assertThat(rows.getString("delivery_email")).isNull();
        assertThat(rows.getString("email")).isEqualTo(email);
        assertThat(rows.getString("password_hash").equals(hash)).as("credential preserved").isTrue();
        assertThat(rows.getObject("created_at", OffsetDateTime.class)).isEqualTo(now);
      }
      assertThatThrownBy(() -> {
        try (var sql = db.createStatement()) {
          sql.executeUpdate("INSERT INTO users (id,email,password_hash,created_at) VALUES ('" + UUID.randomUUID()
            + "','late-unverified@example.invalid','synthetic-hash',CURRENT_TIMESTAMP)");
        }
      }).isInstanceOf(SQLException.class);
    }
    try (var context = new SpringApplicationBuilder(ManagerApiApplication.class).web(WebApplicationType.NONE).run(
      "--spring.datasource.url=" + url(), "--spring.datasource.username=" + username(),
      "--spring.datasource.password=" + password(),
      "--spring.datasource.driver-class-name="
        + (url().startsWith("jdbc:h2:") ? "org.h2.Driver" : "org.postgresql.Driver"),
      "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.baseline-on-migrate=false",
      "--keynest.vault.key-hex=" + HexFormat.of().formatHex(key), "--keynest.vault.key-id=upgrade-v1",
      "--spring.main.banner-mode=off", "--logging.level.root=OFF")) {
      var auth = context.getBean(AuthService.class);
      var retained = auth.requireUser("Bearer " + bearer);
      assertThat(retained.getId()).isEqualTo(owner);
      assertThat(retained.isEmailVerified()).isFalse();
      assertThat(retained.isActivationEligible()).isTrue();
      assertThat(auth.login(email, candidate).userId()).isEqualTo(owner);
      assertThat(context.getBean(VaultService.class).reveal(retained, item).password().equals(secret))
        .as("retained envelope remains readable through the existing session").isTrue();
    } finally {
      java.util.Arrays.fill(key, (byte) 0);
    }
    // Restarting or rerunning migration must not grandfather later accounts.
    assertThat(latest.migrate().migrationsExecuted).isZero();
  }
}
