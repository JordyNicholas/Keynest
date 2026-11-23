package dev.keynest.manager.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.policy.AccountPasswordPolicyService;
import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import dev.keynest.seal.KeynestSeal;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "keynest.rate-limit.enabled=false")
@AutoConfigureMockMvc
class VaultExportIntegrationTest {
  private static final String ACCOUNT = "Synthetic account password!42";
  private static final String BACKUP = "Synthetic backup passphrase!42";
  private static final String SECRET = "aB2#cD3$eF4%gH5&jK6*";
  private static final ObjectMapper JSON = new ObjectMapper();
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired AccountPasswordPolicyService policies;
  @Autowired VaultItemRepository items;
  @Autowired VaultService vault;
  @Autowired VaultExportAdmission admission;
  @Autowired VaultExportSnapshots snapshots;
  @MockitoSpyBean KeynestSeal seal;
  @MockitoSpyBean EncryptedVaultBackup backups;
  @MockitoSpyBean PasswordHashingService passwords;
  private String bearer;
  private UserAccount owner;
  private UUID first;

  @BeforeEach void setup() {
    bearer = "Bearer " + auth.register("export-" + UUID.randomUUID() + "@example.invalid", ACCOUNT).token();
    owner = auth.requireUser(bearer);first = add("Synthetic first", "sample", "https://example.invalid/login");
    clearInvocations(seal, backups, passwords);
  }

  @Test void databaseProjectionMeasuresOnlyOwnedCiphertextLengths() {
    String other = "Bearer " + auth.register("projection-" + UUID.randomUUID() + "@example.invalid", ACCOUNT).token();
    UUID foreign = vault.create(auth.requireUser(other), new VaultService.CreateVaultItemRequest("Other owner", null, null, SECRET, false, null, null, null)).id();
    var result = snapshots.ciphertextLengths(owner, new VaultExportSelection(VaultExportSelection.Scope.ALL, java.util.List.of()));
    assertThat(result).hasSize(1);
    assertThat(result.getFirst()[0]).isEqualTo(first);
    assertThat(((Number) result.getFirst()[1]).intValue()).isEqualTo(items.findById(first).orElseThrow().getEnvelopeBytes().length);
    assertThat(snapshots.ciphertextLengths(owner, new VaultExportSelection(VaultExportSelection.Scope.SELECTED, java.util.List.of(foreign)))).isEmpty();
    assertThat(snapshots.ciphertextLengths(owner, new VaultExportSelection(VaultExportSelection.Scope.SELECTED, java.util.List.of(first)))).hasSize(1);
    assertThat(JSON.valueToTree(policies.inspect(owner)).path("rules").isObject()).isTrue();
    verify(seal, never()).open(any(), any(byte[].class), any(byte[].class));
  }

  @Test void encryptedAllDownloadPreservesSecretsMetadataAndUsesTheReviewedPortableFormat() throws Exception {
    UUID second = add("Synthetic second", null, null);
    ObjectNode selection = all();JsonNode preview = preview(selection);
    assertThat(preview.path("itemCount").intValue()).isEqualTo(2);
    assertThat(preview.path("snapshot").textValue()).matches("[0-9a-f]{64}");
    assertThat(preview.has("password")).isFalse();assertThat(preview.has("items")).isFalse();
    verify(seal, never()).open(any(), any(byte[].class), any(byte[].class));
    var response = mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer)
      .contentType(MediaType.APPLICATION_JSON).content(download(selection, preview).toString()))
      .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
      .andExpect(header().string("Content-Disposition", "attachment; filename=\"keynest-vault.keynest\""))
      .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache"))
      .andExpect(header().string("Expires", "0")).andReturn().getResponse();
    JsonNode file = new EncryptedVaultBackup().decrypt(response.getContentAsByteArray(), BACKUP.toCharArray());
    assertThat(file.path("items").size()).isEqualTo(2);
    for (JsonNode item : file.path("items")) {
      assertThat(item.path("password").textValue()).isEqualTo(SECRET);
      assertThat(item.has("userId")).isFalse();assertThat(item.has("envelopeBytes")).isFalse();
      VaultItem original = items.findById(UUID.fromString(item.path("id").textValue())).orElseThrow();
      assertThat(item.path("createdAt").textValue()).isEqualTo(original.getCreatedAt().toString());
      assertThat(item.path("updatedAt").textValue()).isEqualTo(original.getUpdatedAt().toString());
      assertThat(item.path("expiresAt").textValue()).isEqualTo(original.getExpiresAt().toString());
      assertThat(item.path("lastRotatedAt").textValue()).isEqualTo(original.getLastRotatedAt().toString());
      assertThat(item.path("notifyBeforeDays").intValue()).isEqualTo(original.getNotifyBeforeDays());
      assertThat(item.path("label").textValue()).isEqualTo(original.getLabel());
      assertThat(item.path("policyId").textValue()).isEqualTo(original.getPolicyId());
      if (original.getId().equals(second)) { assertThat(item.path("url").isNull()).isTrue();assertThat(item.path("username").isNull()).isTrue(); }
    }
    assertThat(file.path("accountPolicy").path("id").textValue()).isEqualTo("keynest.default.v1");
    assertThat(response.getContentAsString()).doesNotContain(SECRET, ACCOUNT, BACKUP, "Synthetic first");
  }

  @Test void selectedExportIsExactAndItsSnapshotIsIndependentOfIdOrder() throws Exception {
    UUID second = add("Synthetic second", null, null);add("Not selected", null, null);
    ObjectNode subset = selected(first, second);JsonNode reviewed = preview(subset);
    assertThat(preview(selected(second, first)).path("snapshot")).isEqualTo(reviewed.path("snapshot"));
    byte[] bytes = mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
      .content(download(subset, reviewed).toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    JsonNode opened = new EncryptedVaultBackup().decrypt(bytes, BACKUP.toCharArray());
    assertThat(opened.path("items").size()).isEqualTo(2);
    assertThat(opened.path("items").findValuesAsText("id")).containsExactlyInAnyOrder(first.toString(), second.toString());
  }

  @Test void emptyMissingAndMixedOwnerSelectionsFailWithoutOpeningAnyPassword() throws Exception {
    String other = "Bearer " + auth.register("other-" + UUID.randomUUID() + "@example.invalid", ACCOUNT).token();
    UUID foreign = vault.create(auth.requireUser(other), new VaultService.CreateVaultItemRequest("Other owner", null, null, SECRET, false, null, null, null)).id();
    JsonNode ownedPreview = preview(selected(first));
    for (ObjectNode selection : new ObjectNode[]{selected(first, foreign), selected(first, UUID.randomUUID())}) {
      mvc.perform(post(ApiPaths.VAULT_EXPORT_PREVIEW).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(selection.toString()))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXPORT_SELECTION_UNAVAILABLE"));
      mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
        .content(download(selection, ownedPreview).toString())).andExpect(status().isNotFound()).andExpect(header().doesNotExist("Content-Disposition"));
    }
    mvc.perform(post(ApiPaths.VAULT_EXPORT_PREVIEW).header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
      .content(selected(first).toString())).andExpect(status().isNotFound());
    vault.delete(owner, first);
    mvc.perform(post(ApiPaths.VAULT_EXPORT_PREVIEW).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(all().toString()))
      .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EXPORT_EMPTY"));
    verify(seal, never()).open(any(), any(byte[].class), any(byte[].class));
  }

  @Test void changedMetadataPolicyAndAllItemMembershipInvalidateTheReviewBeforeDecryption() throws Exception {
    ObjectNode selection = all();JsonNode before = preview(selection);
    vault.update(owner, first, new VaultService.UpdateVaultItemRequest("Changed label", null, null, null, null, null, null));
    changed(selection, before);
    before = preview(selection);add("New item", null, null);changed(selection, before);
    before = preview(selection);
    var rules = policies.inspect(owner).rules();
    policies.update(owner, new AccountPasswordPolicyService.Settings("custom", rules));changed(selection, before);
    verify(seal, never()).open(any(), any(byte[].class), any(byte[].class));
  }

  @Test void ciphertextChangesInvalidateTheMarkerEvenWhenTimestampsAreUnchanged() throws Exception {
    ObjectNode selection = selected(first);JsonNode before = preview(selection);
    VaultItem item = items.findById(first).orElseThrow();byte[] changed = item.getEnvelopeBytes().clone();changed[changed.length - 1] ^= 1;
    item.setEnvelopeBytes(changed);items.saveAndFlush(item);changed(selection, before);
    verify(seal, never()).open(any(), any(byte[].class), any(byte[].class));
  }

  @Test void deletedSelectedItemsNeverBecomeAPartialDownload() throws Exception {
    UUID second = add("Second", null, null);ObjectNode selection = selected(first, second);JsonNode before = preview(selection);
    vault.delete(owner, second);
    mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
      .content(download(selection, before).toString())).andExpect(status().isNotFound())
      .andExpect(header().doesNotExist("Content-Disposition"));
    verify(seal, never()).open(any(), any(byte[].class), any(byte[].class));
  }

  @Test void currentPasswordAndSessionAreRequiredAndCredentialsAreConsumed() throws Exception {
    ObjectNode request = download(all(), preview(all()));request.put("accountPassword", "wrong synthetic credential");
    mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
      .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EXPORT_REAUTH_FAILED"));
    mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
      .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    doAnswer(call -> { boolean verified = (boolean) call.callRealMethod();auth.logout(bearer);return verified; })
      .when(passwords).verifyAndClear(any(char[].class), anyString());
    request.put("accountPassword", ACCOUNT);
    mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
      .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Content-Disposition"));
    verify(seal, never()).open(any(), any(byte[].class), any(byte[].class));
  }

  @Test void revocationDuringEncryptionCancelsOutputAndClearsCredentialsAndFile() throws Exception {
    ObjectNode request = download(all(), preview(all()));
    AtomicReference<char[]> phrase = new AtomicReference<>();AtomicReference<byte[]> file = new AtomicReference<>();
    doAnswer(call -> {
      phrase.set(call.getArgument(1));byte[] result = (byte[]) call.callRealMethod();file.set(result);auth.logout(bearer);return result;
    }).when(backups).encrypt(any(), any(char[].class));
    mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
      .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Content-Disposition"));
    assertThat(phrase.get()).containsOnly('\0');assertThat(file.get()).containsOnly((byte) 0);
  }

  @Test void busyRejectionAndEncoderFailureReleaseWorkWithoutReturningAPartialFile() throws Exception {
    ObjectNode request = download(all(), preview(all()));
    try (var work = admission.acquire()) {
      mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EXPORT_BUSY"));
    }
    verify(passwords, never()).verifyAndClear(any(), anyString());
    AtomicReference<char[]> phrase = new AtomicReference<>();
    doAnswer(call -> { phrase.set(call.getArgument(1));throw new IllegalArgumentException("provider-secret-sentinel"); })
      .when(backups).encrypt(any(), any(char[].class));
    mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
      .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EXPORT_DATA_UNAVAILABLE"))
      .andExpect(header().doesNotExist("Content-Disposition"));
    assertThat(phrase.get()).containsOnly('\0');
    assertThatCode(() -> { try (var work = admission.acquire()) { } }).doesNotThrowAnyException();
  }

  @Test void corruptOrOversizedCiphertextFailsClosedWithoutBeingCopiedToAFile() throws Exception {
    VaultItem item = items.findById(first).orElseThrow();item.setEnvelopeBytes(new byte[8193]);items.saveAndFlush(item);
    mvc.perform(post(ApiPaths.VAULT_EXPORT_PREVIEW).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(all().toString()))
      .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EXPORT_DATA_UNAVAILABLE"));
    verify(seal, never()).open(any(), any(byte[].class), any(byte[].class));
  }

  private void changed(ObjectNode selection, JsonNode before) throws Exception {
    mvc.perform(post(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
      .content(download(selection, before).toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXPORT_CHANGED"))
      .andExpect(header().doesNotExist("Content-Disposition"));
  }
  private UUID add(String label, String username, String url) {
    return vault.create(owner, new VaultService.CreateVaultItemRequest(label, url, username, SECRET, false, null, 90, 14)).id();
  }
  private JsonNode preview(ObjectNode selection) throws Exception {
    return JSON.readTree(mvc.perform(post(ApiPaths.VAULT_EXPORT_PREVIEW).header("Authorization", bearer)
      .contentType(MediaType.APPLICATION_JSON).content(selection.toString())).andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsByteArray());
  }
  private static ObjectNode all() { return JSON.createObjectNode().put("destination", "keynest").put("scope", "all"); }
  private static ObjectNode selected(UUID... ids) {
    ObjectNode body = all().put("scope", "selected");var array = body.putArray("itemIds");for (UUID id : ids) array.add(id.toString());return body;
  }
  private static ObjectNode download(ObjectNode selection, JsonNode preview) {
    return selection.deepCopy().put("snapshot", preview.path("snapshot").textValue()).put("accountPassword", ACCOUNT).put("backupPassphrase", BACKUP);
  }
}
