package dev.keynest.manager.vault;

import dev.keynest.manager.ManagerApiApplication;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Actual export/restore between independent application contexts, databases, owners and keys. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresPortableRestoreIntegrationTest {
  private static final String SOURCE = "keynest_portable_source";
  private static final String RECEIVER = "keynest_portable_receiver";
  private static final String ACCOUNT = "Synthetic portable account passphrase!42";
  private static final String PHRASE = "Synthetic separate portable file passphrase";
  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    .withDatabaseName(SOURCE).withUsername("keynest").withPassword(UUID.randomUUID().toString());

  @Test void exportsAndRestoresWithoutTheSourceKeyOrDatabaseAndPersistsAfterRestart() throws Exception {
    String sourceKey = key(), receivingKey = key();assertThat(sourceKey.equals(receivingKey)).isFalse();
    byte[] file;byte[] sourceEnvelope;VaultService.VaultItemView exported;String secret;UUID sourceOwner;
    try (var source = manager(SOURCE, sourceKey, "portable-source")) {
      var auth = source.getBean(AuthService.class);var vault = source.getBean(VaultService.class);
      var owner = auth.requireUser("Bearer " + auth.register("source@example.invalid", ACCOUNT).token());sourceOwner = owner.getId();
      exported = vault.create(owner, new VaultService.CreateVaultItemRequest(
        "Portable example", "https://example.invalid/login", "example", null, true, null, 90, 14));
      exported = vault.get(owner, exported.id()); // Compare database-rounded timestamp precision.
      secret = vault.reveal(owner, exported.id()).password();
      sourceEnvelope = source.getBean(VaultItemRepository.class).findById(exported.id()).orElseThrow().getEnvelopeBytes().clone();
      var snapshots = source.getBean(VaultExportSnapshots.class);
      var selection = new VaultExportSelection(VaultExportSelection.Scope.ALL, List.of());
      var payload = snapshots.preparePayloadForVerifiedUser(owner, selection, snapshots.preview(owner, selection).snapshot());
      try { file = source.getBean(EncryptedVaultBackup.class).encrypt(payload, PHRASE.toCharArray()); }
      finally { payload.removeAll(); }
    }

    var create = postgres.execInContainer("createdb", "--username", postgres.getUsername(), RECEIVER);
    assertThat(create.getExitCode()).as("Create an independent receiving database").isZero();
    UUID restoredId;UUID receiverOwner;
    try (var destination = manager(RECEIVER, receivingKey, "portable-receiver")) {
      var auth = destination.getBean(AuthService.class);
      var owner = auth.requireUser("Bearer " + auth.register("receiver@example.invalid", ACCOUNT).token());receiverOwner = owner.getId();
      var stranger = auth.requireUser("Bearer " + auth.register("unrelated@example.invalid", ACCOUNT).token());
      var vault = destination.getBean(VaultService.class);
      UUID retained = vault.create(owner, new VaultService.CreateVaultItemRequest("Retained", null, null, null, true, null, null, null)).id();
      var restore = destination.getBean(VaultRestoreService.class);
      var preview = restore.preview(owner.getId(), file, PHRASE.toCharArray());
      assertThat(preview.itemCount()).isEqualTo(1);
      assertThat(restore.restore(owner.getId(), file, PHRASE.toCharArray(), preview.snapshot()).restoredCount()).isEqualTo(1);
      var rows = destination.getBean(VaultItemRepository.class).findByUserIdOrderByUpdatedAtDesc(owner.getId());
      assertThat(rows).hasSize(2);
      var row = rows.stream().filter(item -> !item.getId().equals(retained)).findFirst().orElseThrow();restoredId = row.getId();
      assertThat(row.getId()).isNotEqualTo(exported.id());assertThat(row.getUserId()).isNotEqualTo(sourceOwner);
      assertThat(row.getAadVersion()).isEqualTo(VaultItemAad.VERSION_ITEM_V1);
      assertThat(Arrays.equals(row.getEnvelopeBytes(), sourceEnvelope)).isFalse();
      assertThat(secret.equals(vault.reveal(owner, restoredId).password())).as("Restored secret opens under the receiving instance key").isTrue();
      assertThat(row.getCreatedAt()).isEqualTo(exported.createdAt());assertThat(row.getUpdatedAt()).isEqualTo(exported.updatedAt());
      assertThat(row.getExpiresAt()).isEqualTo(exported.expiresAt());assertThat(row.getLastRotatedAt()).isEqualTo(exported.lastRotatedAt());
      assertThat(row.getPolicyId()).isEqualTo(exported.policyId());assertThat(row.getNotifyBeforeDays()).isEqualTo(exported.notifyBeforeDays());
      assertThat(vault.get(owner, retained).label()).isEqualTo("Retained");
      assertThatThrownBy(() -> vault.reveal(stranger, restoredId)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
      var jdbc = destination.getBean(JdbcTemplate.class);
      assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Long.class, sourceOwner)).isZero();
      assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(RECEIVER);
    }

    try (var restarted = manager(RECEIVER, receivingKey, "portable-receiver")) {
      var auth = restarted.getBean(AuthService.class);
      var owner = auth.requireUser("Bearer " + auth.login("receiver@example.invalid", ACCOUNT).token());
      assertThat(owner.getId()).isEqualTo(receiverOwner);
      assertThat(secret.equals(restarted.getBean(VaultService.class).reveal(owner, restoredId).password()))
        .as("Committed restore survives application restart").isTrue();
    } finally { Arrays.fill(file, (byte) 0);Arrays.fill(sourceEnvelope, (byte) 0); }
  }

  private static ConfigurableApplicationContext manager(String database, String keyHex, String keyId) {
    return new SpringApplicationBuilder(ManagerApiApplication.class).web(WebApplicationType.NONE).run(
      "--spring.datasource.url=" + postgres.getJdbcUrl().replace(SOURCE, database),
      "--spring.datasource.username=" + postgres.getUsername(), "--spring.datasource.password=" + postgres.getPassword(),
      "--spring.datasource.driver-class-name=org.postgresql.Driver", "--spring.jpa.hibernate.ddl-auto=validate",
      "--spring.flyway.baseline-on-migrate=false", "--keynest.vault.key-hex=" + keyHex, "--keynest.vault.key-id=" + keyId,
      "--keynest.vault.allow-demo-key=false", "--keynest.tools.demo.enabled=false", "--spring.main.banner-mode=off");
  }

  private static String key() {
    byte[] bytes = new byte[32];new SecureRandom().nextBytes(bytes);
    try { return HexFormat.of().formatHex(bytes); } finally { Arrays.fill(bytes, (byte) 0); }
  }
}
