package dev.keynest.manager;

import dev.keynest.manager.api.ApiPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Low-limit context to prove {@link dev.keynest.manager.api.RateLimitFilter}
 * uses {@link ApiPaths#RATE_LIMITED_POSTS}.
 */
@SpringBootTest(properties = {
  "keynest.rate-limit.enabled=true",
  "keynest.rate-limit.limit=3",
  "keynest.rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void validateIsLimited() throws Exception {
    String body = "{\"password\":\"NotARealPassword!42\",\"usernameOrEmail\":\"user@example.com\"}";
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(post(ApiPaths.TOOLS_VALIDATE)
          .contentType(MediaType.APPLICATION_JSON)
          .content(body))
        .andExpect(status().isOk());
    }
    mockMvc.perform(post(ApiPaths.TOOLS_VALIDATE)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isTooManyRequests());
  }

  @Test
  void generateIsNotRateLimited() throws Exception {
    for (int i = 0; i < 5; i++) {
      mockMvc.perform(post(ApiPaths.TOOLS_GENERATE))
        .andExpect(status().isOk());
    }
  }
}
