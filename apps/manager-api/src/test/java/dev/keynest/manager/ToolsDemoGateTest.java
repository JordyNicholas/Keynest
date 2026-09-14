package dev.keynest.manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate tests: demo seal/open must be off. Overrides
 * {@code application.properties} so this context does not share the local demo-on defaults.
 */
@SpringBootTest(properties = {
  "keynest.tools.demo.enabled=false"
})
@AutoConfigureMockMvc
class ToolsDemoGateTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void sealRejectedWhenDisabled() throws Exception {
    mockMvc.perform(post("/api/v1/tools/seal")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"plaintext\":\"x\"}"))
      .andExpect(status().isForbidden());
  }

  @Test
  void openRejectedWhenDisabled() throws Exception {
    mockMvc.perform(post("/api/v1/tools/open")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"envelopeBase64\":\"x\"}"))
      .andExpect(status().isForbidden());
  }

  @Test
  void generateStillWorksWhenDemoDisabled() throws Exception {
    mockMvc.perform(post("/api/v1/tools/generate"))
      .andExpect(status().isOk());
  }
}
