package dev.keynest.manager;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountPasswordPolicyIntegrationTest {
  private static final String SETTINGS = "/api/v1/account/password-policy";
  @Autowired MockMvc mvc;

  @Test
  void customRulesAreSharedAndIsolatedAndExistingSecretsStayReadable() throws Exception {
    String alice = session();
    String bob = session();
    String item = JsonPath.read(mvc.perform(post("/api/v1/vault/items").header("Authorization", alice)
        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Retained item\",\"generate\":true}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
    String original = JsonPath.read(mvc.perform(post("/api/v1/vault/items/" + item + "/reveal")
        .header("Authorization", alice))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.password");
    String custom = """
      {"mode":"custom","rules":{
        "minLength":8,"maxLength":128,"minCategories":1,
        "requireUpper":false,"requireLower":false,"requireDigit":false,"requireSymbol":false,
        "excludeAmbiguous":false,"forbidWhitespace":false,"forbidUsernameSubstring":false,
        "bannedSubstrings":[],"bannedRegexes":[]
      }}
      """;
    String policy = JsonPath.read(mvc.perform(put(SETTINGS).header("Authorization", alice)
        .contentType(MediaType.APPLICATION_JSON).content(custom))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
    mvc.perform(get("/api/v1/tools/policy").header("Authorization", alice))
      .andExpect(jsonPath("$.id", is(policy))).andExpect(jsonPath("$.rules.minLength", is(8)));
    mvc.perform(get(SETTINGS).header("Authorization", bob))
      .andExpect(jsonPath("$.id", is("keynest.default.v1")));
    mvc.perform(post("/api/v1/tools/validate").header("Authorization", alice)
        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"aabbccdd\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.valid", is(true)));
    mvc.perform(post("/api/v1/tools/generate").header("Authorization", alice))
      .andExpect(status().isOk()).andExpect(jsonPath("$.policyId", is(policy)));
    mvc.perform(post("/api/v1/vault/items").header("Authorization", alice)
        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Custom\",\"password\":\"aabbccdd\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.policyId", is(policy)));
    mvc.perform(post("/api/v1/vault/items").header("Authorization", bob)
        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Default\",\"password\":\"aabbccdd\"}"))
      .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/vault/items/" + item + "/reveal").header("Authorization", alice))
      .andExpect(jsonPath("$.password", is(original)));
    mvc.perform(put("/api/v1/vault/items/" + item).header("Authorization", alice)
        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"aabbccdd\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.policyId", is(policy)));
    mvc.perform(put(SETTINGS).header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
        .content("{\"mode\":\"default\"}")).andExpect(status().isOk());
    mvc.perform(post("/api/v1/vault/items/" + item + "/reveal").header("Authorization", alice))
      .andExpect(status().isOk()).andExpect(jsonPath("$.password", is("aabbccdd")));
    mvc.perform(post("/api/v1/tools/validate").header("Authorization", alice)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"password\":\"aabbccdd\",\"policyId\":\"" + policy + "\"}"))
      .andExpect(status().isConflict());
  }

  @Test
  void policyEditingRequiresAuthenticationAndValidRules() throws Exception {
    mvc.perform(put(SETTINGS).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"default\"}"))
      .andExpect(status().isBadRequest());
    String bearer = session();
    mvc.perform(put(SETTINGS).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
        .content("{\"mode\":\"custom\",\"rules\":{\"minLength\":1}}"))
      .andExpect(status().isBadRequest());
    mvc.perform(get(SETTINGS).header("Authorization", bearer))
      .andExpect(jsonPath("$.id", is("keynest.default.v1")));
  }

  private String session() throws Exception {
    return "Bearer " + JsonPath.read(mvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON).content(
          "{\"email\":\"" + UUID.randomUUID() + "@example.com\",\"password\":\"SyntheticMaster!42\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
  }
}
