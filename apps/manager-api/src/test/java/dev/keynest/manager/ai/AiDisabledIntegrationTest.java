package dev.keynest.manager.ai;

import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "keynest.ai.enabled=false")
@AutoConfigureMockMvc
@Import(AiExplanationIntegrationTest.Config.class)
class AiDisabledIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired AiCoreTest.Fake fake;

  @Test
  void defaultDisabledStateDoesNotCallProviderOrBreakCoreReadiness() throws Exception {
    var session = auth.register("ai-off-" + UUID.randomUUID() + "@example.com", "SyntheticMaster!42");
    mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", "Bearer " + session.token())
        .header("X-Keynest-AI-Key", AiCoreTest.KEY).contentType(MediaType.APPLICATION_JSON)
        .content("{\"providerId\":\"test\",\"policyId\":\"keynest.default.v1\"}"))
      .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("AI_DISABLED"));
    assertThat(fake.calls).isZero();
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get(ApiPaths.VAULT_ITEMS).header("Authorization", "Bearer " + session.token()))
      .andExpect(status().isOk());
    mvc.perform(post(ApiPaths.TOOLS_GENERATE)).andExpect(status().isOk());
  }
}
