package dev.keynest.manager.migration;

import dev.keynest.manager.ManagerApiApplication;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.AuthSessionRepository;
import dev.keynest.manager.vault.VaultItemRepository;
import dev.keynest.manager.vault.VaultService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real dump/restore, confined to a disposable container and a separate restore database. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresRestoreIntegrationTest {

  private static final String SOURCE_DB = "keynest_backup_source";
  private static final String RESTORED_DB = "keynest_backup_restored";
  private static final String DUMP_PATH = "/tmp/keynest-restore-rehearsal.dump";

  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(
    DockerImageName.parse("postgres:16-alpine")
  )
    .withDatabaseName(SOURCE_DB)
    .withUsername("keynest")
    .withPassword(UUID.randomUUID().toString());

  @Test
  void restoresAccountsSessionsAndReadableVaultWithTheRetainedKey() throws Exception {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    String keyHex = HexFormat.of().formatHex(key);
    Arrays.fill(key, (byte) 0);
    String email = "restore-" + UUID.randomUUID() + "@example.com";
    String password = UUID.randomUUID() + "Aa1!";
    AuthService.AuthResponse session;
    UUID itemId;
    byte[] envelope;
    String expectedSecret;
    String sessionDigest;

    // Closing the source context stops application writers before the backup.
    try (ConfigurableApplicationContext source = startManager(SOURCE_DB, keyHex)) {
      AuthService auth = source.getBean(AuthService.class);
      VaultService vault = source.getBean(VaultService.class);
      session = auth.register(email, password);
      sessionDigest = source.getBean(AuthSessionRepository.class).findAll().getFirst().getTokenDigest();
      var user = auth.requireUser("Bearer " + session.token());
      itemId = vault.create(user, new VaultService.CreateVaultItemRequest(
        "Restore rehearsal", "https://example.com", "demo-user", null,
        true, null, 90, 14
      )).id();
      expectedSecret = vault.reveal(user, itemId).password();
      envelope = source.getBean(VaultItemRepository.class)
        .findByIdAndUserId(itemId, user.getId()).orElseThrow().getEnvelopeBytes().clone();
    }

    runPostgres("pg_dump", "--username", postgres.getUsername(), "--dbname", SOURCE_DB,
      "--format=custom", "--file", DUMP_PATH);
    runPostgres("createdb", "--username", postgres.getUsername(), RESTORED_DB);
    runPostgres("pg_restore", "--username", postgres.getUsername(), "--dbname", RESTORED_DB,
      "--exit-on-error", "--no-owner", DUMP_PATH);

    // No schema reset: startup must validate the restored Flyway history and JPA mappings.
    try (ConfigurableApplicationContext restored = startManager(RESTORED_DB, keyHex)) {
      JdbcTemplate jdbc = restored.getBean(JdbcTemplate.class);
      assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(RESTORED_DB);
      assertThat(restored.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("4");
      restored.getBean(Flyway.class).validate();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1L);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions", Long.class)).isEqualTo(1L);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vault_items", Long.class)).isEqualTo(1L);

      AuthService auth = restored.getBean(AuthService.class);
      var user = auth.requireUser("Bearer " + session.token());
      assertThat(user.getId()).isEqualTo(session.userId());
      assertThat(restored.getBean(AuthSessionRepository.class)
        .findByTokenDigest(sessionDigest).isPresent()).isTrue();
      var item = restored.getBean(VaultItemRepository.class)
        .findByIdAndUserId(itemId, user.getId()).orElseThrow();
      assertThat(Arrays.equals(envelope, item.getEnvelopeBytes()))
        .as("restored envelope bytes match the backup source").isTrue();
      String restoredSecret = restored.getBean(VaultService.class).reveal(user, itemId).password();
      // Boolean assertions avoid exposing generated secrets if a regression fails.
      assertThat(expectedSecret.equals(restoredSecret)).as("restored secret is readable with retained key").isTrue();
      assertThat(auth.login(email, password).userId()).isEqualTo(session.userId());
    }
  }

  private static ConfigurableApplicationContext startManager(String database, String keyHex) {
    return new SpringApplicationBuilder(ManagerApiApplication.class)
      .web(WebApplicationType.NONE)
      .run(
        "--spring.datasource.url=" + postgres.getJdbcUrl().replace(SOURCE_DB, database),
        "--spring.datasource.username=" + postgres.getUsername(),
        "--spring.datasource.password=" + postgres.getPassword(),
        "--spring.datasource.driver-class-name=org.postgresql.Driver",
        "--spring.jpa.hibernate.ddl-auto=validate",
        "--spring.flyway.baseline-on-migrate=false",
        "--keynest.vault.key-hex=" + keyHex,
        "--keynest.vault.key-id=restore-rehearsal",
        "--keynest.vault.allow-demo-key=false",
        "--keynest.tools.demo.enabled=false",
        "--spring.main.banner-mode=off"
      );
  }

  private static void runPostgres(String... command) throws Exception {
    var result = postgres.execInContainer(command);
    // Dump contents and command output remain inside the disposable test environment.
    assertThat(result.getExitCode()).as("PostgreSQL rehearsal command succeeded: " + command[0]).isZero();
  }
}
