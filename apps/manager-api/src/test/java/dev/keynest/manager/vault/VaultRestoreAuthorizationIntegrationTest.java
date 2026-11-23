package dev.keynest.manager.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "keynest.rate-limit.enabled=false")
@AutoConfigureMockMvc
class VaultRestoreAuthorizationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired VaultExportSnapshots exports;
  @Autowired EncryptedVaultBackup backups;
  @Autowired VaultItemRepository items;
  @Autowired PlatformTransactionManager transactions;
  @MockitoSpyBean VaultService vault;
  @Test void independentlyCommittedRevocationDuringReceivingWorkRollsBackEveryInsert() throws Exception {
    String account = "Synthetic account passphrase!42", phrase = "Synthetic separate file passphrase";
    var source = auth.requireUser("Bearer " + auth.register("restore-auth-source-" + UUID.randomUUID() + "@example.invalid", account).token());
    vault.create(source, new VaultService.CreateVaultItemRequest("Synthetic", null, null, null, true, null, null, null));
    var selection = new VaultExportSelection(VaultExportSelection.Scope.ALL, java.util.List.of());
    var payload = exports.preparePayloadForVerifiedUser(source, selection, exports.preview(source, selection).snapshot());
    String file;try { file = Base64.getEncoder().encodeToString(backups.encrypt(payload, phrase.toCharArray())); } finally { payload.removeAll(); }
    String bearer = "Bearer " + auth.register("restore-auth-receiver-" + UUID.randomUUID() + "@example.invalid", account).token();
    var receiver = auth.requireUser(bearer);var json = new ObjectMapper();var body = json.createObjectNode().put("file", file).put("backupPassphrase", phrase);
    byte[] preview = mvc.perform(post(ApiPaths.VAULT_RESTORE_PREVIEW).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    String plan = json.readTree(preview).path("planId").textValue();
    var independent = new TransactionTemplate(transactions);independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    doAnswer(call -> { Object envelope = call.callRealMethod();independent.executeWithoutResult(status -> auth.logout(bearer));return envelope; })
      .when(vault).sealPassword(anyString(), eq(receiver.getId()), any(), anyString());
    mvc.perform(post(ApiPaths.VAULT_RESTORE_APPLY).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
      .content(body.put("planId", plan).put("accountPassword", account).toString())).andExpect(status().isUnauthorized());
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver.getId())).isEmpty();
    assertThatThrownBy(() -> auth.requireUser(bearer)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
}
