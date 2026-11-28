package dev.keynest.manager.migration;

import dev.keynest.manager.ManagerApiApplication;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.PasswordChangeService;
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
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    .withDatabaseName(SOURCE_DB).withUsername("keynest").withPassword(UUID.randomUUID().toString());

  @Test
  void restoresAccountsSessionsAndReadableVaultWithTheRetainedKey() throws Exception {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    String keyHex = HexFormat.of().formatHex(key);
    Arrays.fill(key, (byte) 0);
    String email = "restore-" + UUID.randomUUID() + "@example.com";
    String password = UUID.randomUUID() + "Aa1!";
    String newPassword = UUID.randomUUID() + "Bb2!";
    AuthService.AuthResponse session;
    UUID itemId;
    byte[] envelope;
    String expectedSecret;
    String sessionDigest;
    char[][] pendingProof = new char[1][];
    String pendingAddress = "pending-" + UUID.randomUUID() + "@example.invalid";

    try {
      // Closing the source context stops application writers before the backup.
      try (ConfigurableApplicationContext source = startManager(SOURCE_DB, keyHex)) {
        AuthService auth = source.getBean(AuthService.class);
        VaultService vault = source.getBean(VaultService.class);
        session = source.getBean(dev.keynest.manager.testing.TestAccounts.class).register(email, password);
        var user = auth.requireUser("Bearer " + session.token());
        itemId = vault.create(user, new VaultService.CreateVaultItemRequest("Restore rehearsal", "https://example.com",
          "demo-user", null, true, null, 90, 14)).id();
        expectedSecret = vault.reveal(user, itemId).password();
        envelope = source.getBean(VaultItemRepository.class).findByIdAndUserId(itemId, user.getId()).orElseThrow()
          .getEnvelopeBytes().clone();
        session = source.getBean(PasswordChangeService.class).change("Bearer " + session.token(),
          password.toCharArray(), newPassword.toCharArray());
        sessionDigest = source.getBean(AuthSessionRepository.class).findAll().getFirst().getTokenDigest();
        var state = source.getBean(dev.keynest.manager.auth.verification.VerificationStore.class);
        assertThat(state.admit(new dev.keynest.manager.auth.verification.VerificationRequest(
          dev.keynest.manager.auth.verification.VerificationRequest.Purpose.NEW_ACCOUNT, pendingAddress, pendingAddress,
          null), "disposable-restore-client"))
          .isEqualTo(dev.keynest.manager.auth.verification.VerificationStore.Admission.ACCEPTED);
        try (var claimed = state.claimNext().orElseThrow()) {
          claimed.useToken(value -> pendingProof[0] = value.clone());
        }
      }

      runPostgres("pg_dump", "--username", postgres.getUsername(), "--dbname", SOURCE_DB, "--format=custom", "--file",
        DUMP_PATH);
      // Commit consumption after backup: restoring the older database must not re-authorize this link.
      try (ConfigurableApplicationContext used = startManager(SOURCE_DB, keyHex)) {
        used.getBean(dev.keynest.manager.auth.AccountVerificationService.class).activate(pendingProof[0].clone(),
          (UUID.randomUUID() + " Aa1!").toCharArray());
      }
      runPostgres("createdb", "--username", postgres.getUsername(), RESTORED_DB);
      runPostgres("pg_restore", "--username", postgres.getUsername(), "--dbname", RESTORED_DB, "--exit-on-error",
        "--no-owner", DUMP_PATH);

      // No schema reset: startup must validate the restored Flyway history and JPA mappings.
      try (ConfigurableApplicationContext restored = startManager(RESTORED_DB, keyHex)) {
        JdbcTemplate jdbc = restored.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(RESTORED_DB);
        assertThat(restored.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("9");
        restored.getBean(Flyway.class).validate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vault_items", Long.class)).isEqualTo(1L);

        AuthService auth = restored.getBean(AuthService.class);
        var user = auth.requireUser("Bearer " + session.token());
        assertThat(user.getId()).isEqualTo(session.userId());
        assertThat(restored.getBean(AuthSessionRepository.class).findByTokenDigest(sessionDigest).isPresent()).isTrue();
        var item = restored.getBean(VaultItemRepository.class).findByIdAndUserId(itemId, user.getId()).orElseThrow();
        assertThat(Arrays.equals(envelope, item.getEnvelopeBytes()))
          .as("restored envelope bytes match the backup source").isTrue();
        String restoredSecret = restored.getBean(VaultService.class).reveal(user, itemId).password();
        // Boolean assertions avoid exposing generated secrets if a regression fails.
        assertThat(expectedSecret.equals(restoredSecret)).as("restored secret is readable with retained key").isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> auth.login(email, password))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(auth.login(email, newPassword).userId()).isEqualTo(session.userId());
        // Characterize rollback risk in a non-Web context; no worker/endpoints are enabled.
        assertThat(restored.getBean(dev.keynest.manager.auth.verification.VerificationStore.class).canConsume(
          pendingProof[0].clone(), dev.keynest.manager.auth.verification.VerificationRequest.Purpose.NEW_ACCOUNT, null))
          .as("an older database can revive a consumed pending proof before explicit recovery invalidation").isTrue();
      }

      String recovery;
      try (var input = getClass().getResourceAsStream("/recovery/invalidate-restored-authority.sql")) {
        assertThat(input).isNotNull();
        recovery = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      }
      // All contexts/writers are closed. Run the exact reviewed operator script against only this disposable database.
      runPostgres("psql", "--username", postgres.getUsername(), "--dbname", RESTORED_DB, "--set", "ON_ERROR_STOP=1",
        "--command", recovery);
      try (ConfigurableApplicationContext recovered = startManager(RESTORED_DB, keyHex)) {
        JdbcTemplate jdbc = recovered.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenges", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_delivery_jobs", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions", Long.class)).isZero();
        assertThat(recovered.getBean(dev.keynest.manager.auth.verification.VerificationStore.class).canConsume(
          pendingProof[0].clone(), dev.keynest.manager.auth.verification.VerificationRequest.Purpose.NEW_ACCOUNT, null))
          .isFalse();
        AuthService auth = recovered.getBean(AuthService.class);
        String restoredBearer = session.token();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> auth.requireUser(restoredBearer))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var fresh = auth.login(email, newPassword);
        var owner = auth.requireUser(fresh.token());
        assertThat(owner.getId()).isEqualTo(session.userId());
        assertThat(Arrays.equals(envelope, recovered.getBean(VaultItemRepository.class)
          .findByIdAndUserId(itemId, owner.getId()).orElseThrow().getEnvelopeBytes())).isTrue();
        assertThat(expectedSecret.equals(recovered.getBean(VaultService.class).reveal(owner, itemId).password()))
          .as("explicit recovery invalidation preserves readable Vault data and the retained key").isTrue();
      }
    } finally {
      if (pendingProof[0] != null)
        Arrays.fill(pendingProof[0], '\0');
    }
  }

  private static ConfigurableApplicationContext startManager(String database, String keyHex) {
    return new SpringApplicationBuilder(ManagerApiApplication.class).web(WebApplicationType.NONE).run(
      "--spring.datasource.url=" + postgres.getJdbcUrl().replace(SOURCE_DB, database),
      "--spring.datasource.username=" + postgres.getUsername(),
      "--spring.datasource.password=" + postgres.getPassword(),
      "--spring.datasource.driver-class-name=org.postgresql.Driver", "--spring.jpa.hibernate.ddl-auto=validate",
      "--spring.flyway.baseline-on-migrate=false", "--keynest.vault.key-hex=" + keyHex,
      "--keynest.vault.key-id=restore-rehearsal", "--keynest.vault.allow-demo-key=false",
      "--keynest.tools.demo.enabled=false", "--spring.main.banner-mode=off");
  }

  private static void runPostgres(String... command) throws Exception {
    var result = postgres.execInContainer(command);
    // Dump contents and command output remain inside the disposable test environment.
    assertThat(result.getExitCode()).as("PostgreSQL rehearsal command succeeded: " + command[0]).isZero();
  }
}
