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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    String token = mockMvc.getDispatcherServlet().getWebApplicationContext()
      .getBean(dev.keynest.manager.testing.TestAccounts.class).register(email, password).token();

    MvcResult created = mockMvc
      .perform(post(ApiPaths.VAULT_ITEMS).header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content("""
          {
            "label":"Example",
            "url":"https://example.com",
            "username":"demo",
            "generate":true,
            "expiresInDays":90
          }
          """))
      .andExpect(status().isOk()).andExpect(jsonPath("$.label", is("Example")))
      .andExpect(jsonPath("$.policyId", is("keynest.default.v1"))).andReturn();

    String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

    org.assertj.core.api.Assertions.assertThat(vaultItems.findById(UUID.fromString(id)).orElseThrow().getAadVersion())
      .isEqualTo(1);

    mockMvc.perform(get(ApiPaths.VAULT_ITEMS).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1)));

    mockMvc.perform(post(ApiPaths.vaultItemReveal(id)).header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.item.id", is(id)))
      .andExpect(jsonPath("$.password", notNullValue()));

    mockMvc.perform(delete(ApiPaths.vaultItem(id)).header("Authorization", "Bearer " + token))
      .andExpect(status().isOk());

    mockMvc.perform(get(ApiPaths.VAULT_ITEMS).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void duplicateCreationUsesOnlyTheOwnersLoginFieldsAndPassword() throws Exception {
    String owner = registerForCreation("duplicate-owner@example.test");
    String other = registerForCreation("duplicate-other@example.test");
    String login = """
      {"label":"Original","username":"saved-login","url":"https://example.test/a","password":"Distant!Creek47%Cedar"}
      """;
    create(owner, login).andExpect(status().isOk());
    create(owner, login.replace("Original", "Different label").replace("saved-login", "  saved-login  "))
      .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.code", is("VAULT_ITEM_DUPLICATE"))).andExpect(jsonPath("$.password").doesNotExist());
    create(owner, login.replace("Cedar", "Aspen")).andExpect(status().isOk());
    create(owner, login.replace("saved-login", "another-login")).andExpect(status().isOk());
    create(owner, login.replace("example.test/a", "example.test/b")).andExpect(status().isOk());
    create(other, login).andExpect(status().isOk());
    mockMvc.perform(get(ApiPaths.VAULT_ITEMS).header("Authorization", "Bearer " + owner)).andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(4)));
  }

  @Test
  void duplicateCreationMatchesMissingAndBlankUrls() throws Exception {
    String owner = registerForCreation("duplicate-no-url@example.test");
    create(owner, """
      {"label":"Original","username":"saved-login","password":"Distant!Creek47%Cedar"}
      """).andExpect(status().isOk());
    create(owner, """
      {"label":"Another label","username":"saved-login","url":"  ","password":"Distant!Creek47%Cedar"}
      """).andExpect(status().isConflict()).andExpect(jsonPath("$.code", is("VAULT_ITEM_DUPLICATE")));
    create(owner, """
      {"label":"With URL","username":"saved-login","url":"https://example.test","password":"Distant!Creek47%Cedar"}
      """).andExpect(status().isOk());
  }

  @Test
  void createChecksThePasswordAgainstTheSavedUsername() throws Exception {
    String owner = registerForCreation("trimmed-username@example.test");
    create(owner, """
      {"label":"Example","username":"  creek  ","password":"Distant!Creek47%Cedar"}
      """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("VAULT_PASSWORD_POLICY")))
      .andExpect(jsonPath("$.violations[0]", is("password.contains_username")));
  }

  @Test
  void createAndEditReturnBadRequestForOversizedMetadata() throws Exception {
    String owner = registerForCreation("oversized-metadata@example.test");
    MvcResult saved = create(owner, """
      {"label":"Example","password":"Distant!Creek47%Cedar"}
      """).andExpect(status().isOk()).andReturn();
    String id = JsonPath.read(saved.getResponse().getContentAsString(), "$.id");

    for (String fields : List.of("\"label\":\"" + "a".repeat(201) + "\"",
      "\"label\":\"Example\",\"username\":\"" + "a".repeat(321) + "\"",
      "\"label\":\"Example\",\"url\":\"" + "a".repeat(2001) + "\"")) {
      create(owner, "{" + fields + ",\"password\":\"Distant!Creek47%Aspen\"}").andExpect(status().isBadRequest());
      mockMvc.perform(put(ApiPaths.vaultItem(id)).header("Authorization", "Bearer " + owner)
        .contentType(MediaType.APPLICATION_JSON).content("{" + fields + "}")).andExpect(status().isBadRequest());
    }
    mockMvc.perform(get(ApiPaths.VAULT_ITEMS).header("Authorization", "Bearer " + owner)).andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].label", is("Example")));
  }

  @Test
  void policyOnlyLegacyEnvelopeRemainsReadable() throws Exception {
    String token = mockMvc.getDispatcherServlet().getWebApplicationContext()
      .getBean(dev.keynest.manager.testing.TestAccounts.class)
      .register("legacy-vault-user@example.com", "MasterPassphrase!42").token();
    UUID userId = mockMvc.getDispatcherServlet().getWebApplicationContext()
      .getBean(dev.keynest.manager.auth.AuthService.class).requireUser("Bearer " + token).getId();
    UUID itemId = UUID.randomUUID();
    String plaintext = "Legacy!Creek47%Cedar";
    KeynestSeal seal = new KeynestSeal();
    byte[] envelope = seal
      .seal(plaintext.getBytes(StandardCharsets.UTF_8), HexFormat.of().parseHex(VaultKeyProperties.DEMO_KEY_HEX),
        "vault-v1", PasswordPolicy.DEFAULT_POLICY_ID.getBytes(StandardCharsets.UTF_8))
      .toBytes();
    Instant now = Instant.now();
    vaultItems.save(new VaultItem(itemId, userId, "Legacy item", null, null, envelope, PasswordPolicy.DEFAULT_POLICY_ID,
      null, null, now, 14, now, now));

    mockMvc.perform(post(ApiPaths.vaultItemReveal(itemId.toString())).header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.password", is(plaintext)));

    create(token, """
      {"label":"Legacy duplicate","password":"%s"}
      """.formatted(plaintext)).andExpect(status().isConflict())
      .andExpect(jsonPath("$.code", is("VAULT_ITEM_DUPLICATE")));
  }

  private String registerForCreation(String email) throws Exception {
    return mockMvc.getDispatcherServlet().getWebApplicationContext()
      .getBean(dev.keynest.manager.testing.TestAccounts.class).register(email, "MasterPassphrase!42").token();
  }

  private ResultActions create(String token, String json) throws Exception {
    return mockMvc.perform(post(ApiPaths.VAULT_ITEMS).header("Authorization", "Bearer " + token)
      .contentType(MediaType.APPLICATION_JSON).content(json));
  }
}
