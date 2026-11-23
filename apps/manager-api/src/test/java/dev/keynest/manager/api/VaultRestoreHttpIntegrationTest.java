package dev.keynest.manager.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.manager.vault.*;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "keynest.rate-limit.enabled=false")
@AutoConfigureMockMvc
class VaultRestoreHttpIntegrationTest {
  private static final String ACCOUNT = "Synthetic account passphrase!42", PHRASE = "Synthetic portable backup passphrase";
  private static final ObjectMapper JSON = new ObjectMapper();
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired VaultService vault;
  @Autowired VaultExportSnapshots exports;
  @Autowired VaultItemRepository items;
  @Autowired EncryptedVaultBackup backups;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean PasswordHashingService passwords;
  private String bearer, encoded;
  private UUID receiver;
  @BeforeEach void setup() {
    String sourceToken = "Bearer " + auth.register("restore-source-" + UUID.randomUUID() + "@example.invalid", ACCOUNT).token();
    var source = auth.requireUser(sourceToken);
    vault.create(source, new VaultService.CreateVaultItemRequest("Synthetic item", null, null, null, true, null, 90, 14));
    var selection = new VaultExportSelection(VaultExportSelection.Scope.ALL, java.util.List.of());
    var payload = exports.preparePayloadForVerifiedUser(source, selection, exports.preview(source, selection).snapshot());
    try { encoded = Base64.getEncoder().encodeToString(backups.encrypt(payload, PHRASE.toCharArray())); } finally { payload.removeAll(); }
    bearer = "Bearer " + auth.register("restore-receiver-" + UUID.randomUUID() + "@example.invalid", ACCOUNT).token();receiver = auth.requireUser(bearer).getId();
  }
  @Test void previewsAndAppliesExactlyOnceWithNoStoreAndNoPlaintextResponse() throws Exception {
    String plan = preview(bearer);assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver)).isEmpty();
    var request = apply(plan);
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.restoredCount").value(1)).andExpect(header().string("Cache-Control", "no-store"));
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver)).hasSize(1);
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
      .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESTORE_PLAN_INVALID"));
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver)).hasSize(1);
  }
  @Test void wrongPasswordDoesNotConsumeThePlanButChangedFileDoes() throws Exception {
    String plan = preview(bearer);
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
      .content(apply(plan).put("accountPassword", "wrong synthetic password").toString())).andExpect(status().isForbidden());
    byte[] changed = Base64.getDecoder().decode(encoded);changed[changed.length - 1] ^= 1;
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
      .content(apply(plan).put("file", Base64.getEncoder().encodeToString(changed)).toString())).andExpect(status().isConflict());
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
      .content(apply(plan).toString())).andExpect(status().isConflict());
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver)).isEmpty();
  }
  @Test void anotherOwnerCannotUseThePlan() throws Exception {
    String plan = preview(bearer);
    String other = "Bearer " + auth.register("restore-other-" + UUID.randomUUID() + "@example.invalid", ACCOUNT).token();
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(apply(plan).toString()))
      .andExpect(status().isConflict());
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(auth.requireUser(other).getId())).isEmpty();
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver)).isEmpty();
  }
  @Test void differentSessionAndCredentialEpochCannotApplyAnOldPreview() throws Exception {
    String plan = preview(bearer);String email = auth.requireUser(bearer).getEmail();
    String second = "Bearer " + auth.login(email, ACCOUNT).token();
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", second).contentType(MediaType.APPLICATION_JSON).content(apply(plan).toString())).andExpect(status().isConflict());
    String nextPassword = "Different synthetic account passphrase!42";
    jdbc.update("update users set password_hash = ? where id = ?", passwords.hash(nextPassword), receiver);
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
      .content(apply(plan).put("accountPassword", nextPassword).toString())).andExpect(status().isConflict());
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver)).isEmpty();
  }
  @Test void revocationAfterReauthenticationPreventsAllWrites() throws Exception {
    String plan = preview(bearer);
    doAnswer(call -> { boolean result = (boolean) call.callRealMethod();auth.logout(bearer);return result; }).when(passwords).verifyAndClear(any(), anyString());
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(apply(plan).toString())).andExpect(status().isUnauthorized());
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver)).isEmpty();
  }
  @Test void malformedDuplicateQueryAndOversizedBodiesFailClosedAndDoNotPoisonAdmission() throws Exception {
    for (String invalid : new String[]{"{}", "{\"file\":\"AA==\",\"file\":\"AA==\",\"backupPassphrase\":\"Synthetic long passphrase\"}", body().put("unknown", "value").toString(), body().toString() + "{}"}) {
      mvc.perform(post(ApiPaths.VAULT_RESTORE_PREVIEW).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(invalid))
        .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
    }
    mvc.perform(post(ApiPaths.VAULT_RESTORE_PREVIEW + "?file=no").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body().toString())).andExpect(status().isBadRequest());
    mvc.perform(post(ApiPaths.VAULT_RESTORE_PREVIEW).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(new byte[VaultRestoreRequest.MAX_BYTES + 1])).andExpect(status().isPayloadTooLarge());
    mvc.perform(post(ApiPaths.VAULT_RESTORE_PREVIEW).contentType(MediaType.APPLICATION_JSON).content(body().toString())).andExpect(status().isUnauthorized());
    preview(bearer);assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver)).isEmpty();
  }
  private String preview(String token) throws Exception {
    byte[] response = mvc.perform(post(ApiPaths.VAULT_RESTORE_PREVIEW).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body().toString()))
      .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.itemCount").value(1))
      .andExpect(jsonPath("$.snapshot").doesNotExist()).andReturn().getResponse().getContentAsByteArray();
    return JSON.readTree(response).path("planId").textValue();
  }
  private ObjectNode body() { return JSON.createObjectNode().put("file", encoded).put("backupPassphrase", PHRASE); }
  private ObjectNode apply(String plan) { return body().put("accountPassword", ACCOUNT).put("planId", plan); }
}
