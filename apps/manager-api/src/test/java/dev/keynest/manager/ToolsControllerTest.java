package dev.keynest.manager;

import com.jayway.jsonpath.JsonPath;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.seal.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Happy-path tools tests with local demo seal/open enabled
 * ({@code keynest.tools.demo.enabled=true} from {@code application.properties}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ToolsControllerTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void health() throws Exception {
    mockMvc.perform(get(ApiPaths.TOOLS_HEALTH))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("ok")))
      .andExpect(jsonPath("$.policyId", is("keynest.default.v1")));
  }

  @Test
  void policyEndpointReturnsSchemaBackedDocument() throws Exception {
    mockMvc.perform(get(ApiPaths.TOOLS_POLICY))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is("keynest.default.v1")))
      .andExpect(jsonPath("$.rules.minLength", is(16)))
      .andExpect(jsonPath("$.appliesTo[0]").exists());
  }

  @Test
  void generateProducesValidPassword() throws Exception {
    mockMvc.perform(post(ApiPaths.TOOLS_GENERATE))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.valid", is(true)))
      .andExpect(jsonPath("$.password").isString());
  }

  @Test
  void verifiesGeneratedHashesWithinTheHttpWorkBudget() throws Exception {
    String password = "SyntheticToolPassword!42";
    MvcResult hashed = mockMvc.perform(post(ApiPaths.TOOLS_HASH_PASSWORD)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"password\":\"%s\"}".formatted(password)))
      .andExpect(status().isOk())
      .andReturn();
    String defaultHash = JsonPath.read(hashed.getResponse().getContentAsString(), "$.hash");
    // This small fixture is inexpensive but uses one iteration above the HTTP policy.
    String customHash = new PasswordHasher(4, 8192, 1).hashPassword(password.toCharArray());

    for (String hash : new String[] {defaultHash, customHash, "invalid"}) {
      mockMvc.perform(post(ApiPaths.TOOLS_VERIFY_PASSWORD)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"password\":\"%s\",\"hash\":\"%s\"}".formatted(password, hash)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid", is(hash.equals(defaultHash))));
    }
  }

  @Test
  void sealOpenRoundTrip() throws Exception {
    MvcResult sealed = mockMvc.perform(post(ApiPaths.TOOLS_SEAL)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"plaintext\":\"vault-secret-value\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.envelopeBase64").isString())
      .andReturn();

    String body = sealed.getResponse().getContentAsString();
    String envelope = body.replaceAll("(?s).*\"envelopeBase64\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    mockMvc.perform(post(ApiPaths.TOOLS_OPEN)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"envelopeBase64\":\"" + envelope + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.plaintext", is("vault-secret-value")));
  }
}
