package dev.keynest.manager;

import com.jayway.jsonpath.JsonPath;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.config.VaultKeyProperties;
import dev.keynest.manager.vault.VaultItem;
import dev.keynest.manager.vault.VaultItemRepository;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.seal.KeynestSeal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VaultAuthIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  VaultItemRepository vaultItems;

  @Test
  void registerCreateRevealAndDeleteVaultItem() throws Exception {
    String email = "vault-user@example.com";
    String password = "MasterPassphrase!42";

    MvcResult register = mockMvc.perform(post(ApiPaths.AUTH_REGISTER)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"email":"%s","password":"%s"}
          """.formatted(email, password)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.token", notNullValue()))
      .andReturn();

    String token = JsonPath.read(register.getResponse().getContentAsString(), "$.token");

    MvcResult created = mockMvc.perform(post(ApiPaths.VAULT_ITEMS)
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "label":"Example",
            "url":"https://example.com",
            "username":"demo",
            "generate":true,
            "expiresInDays":90
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.label", is("Example")))
      .andExpect(jsonPath("$.policyId", is("keynest.default.v1")))
      .andReturn();

    String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

    org.assertj.core.api.Assertions.assertThat(
        vaultItems.findById(UUID.fromString(id)).orElseThrow().getAadVersion()
      )
      .isEqualTo(1);

    mockMvc.perform(get(ApiPaths.VAULT_ITEMS)
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1)));

    mockMvc.perform(get(ApiPaths.vaultItem(id))
        .param("reveal", "true")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.item.id", is(id)))
      .andExpect(jsonPath("$.password", notNullValue()));

    mockMvc.perform(delete(ApiPaths.vaultItem(id))
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk());

    mockMvc.perform(get(ApiPaths.VAULT_ITEMS)
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void policyOnlyLegacyEnvelopeRemainsReadable() throws Exception {
    MvcResult register = mockMvc.perform(post(ApiPaths.AUTH_REGISTER)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"email":"legacy-vault-user@example.com","password":"MasterPassphrase!42"}
          """))
      .andExpect(status().isOk())
      .andReturn();

    String token = JsonPath.read(register.getResponse().getContentAsString(), "$.token");
    UUID userId = UUID.fromString(JsonPath.read(
        register.getResponse().getContentAsString(), "$.userId"
    ));
    UUID itemId = UUID.randomUUID();
    String plaintext = "LegacyVaultPassword!42";
    KeynestSeal seal = new KeynestSeal();
    byte[] envelope = seal.seal(
        plaintext.getBytes(StandardCharsets.UTF_8),
        HexFormat.of().parseHex(VaultKeyProperties.DEMO_KEY_HEX),
        "vault-v1",
        PasswordPolicy.DEFAULT_POLICY_ID.getBytes(StandardCharsets.UTF_8)
      )
      .toBytes();
    Instant now = Instant.now();
    vaultItems.save(new VaultItem(
        itemId,
        userId,
        "Legacy item",
        null,
        null,
        envelope,
        PasswordPolicy.DEFAULT_POLICY_ID,
        null,
        null,
        now,
        14,
        now,
        now
    ));

    mockMvc.perform(get(ApiPaths.vaultItem(itemId.toString()))
        .param("reveal", "true")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.password", is(plaintext)));
  }
}
