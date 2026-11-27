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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

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
    mockMvc.perform(post("/keynest" + ApiPaths.TOOLS_VALIDATE)
        .contextPath("/keynest")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isTooManyRequests());
    mockMvc.perform(post(ApiPaths.TOOLS_VALIDATE)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isTooManyRequests());
  }

  @Test
  void revokeAllSharesCanonicalRateAccountingAndNoStoreBeforeAuthentication() throws Exception {
    for (int index = 0; index < 3; index++) {
      mockMvc.perform(post(ApiPaths.AUTH_LOGOUT_ALL).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    }
    mockMvc.perform(post("/keynest" + ApiPaths.AUTH_LOGOUT_ALL).contextPath("/keynest")
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isTooManyRequests()).andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void passwordChangeSharesCanonicalRateAccountingAndNoStoreBeforeAuthentication() throws Exception {
    for (int index = 0; index < 3; index++) {
      mockMvc.perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    }
    mockMvc.perform(post("/keynest" + ApiPaths.AUTH_PASSWORD_CHANGE).contextPath("/keynest")
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isTooManyRequests()).andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void generateIsNotRateLimited() throws Exception {
    for (int i = 0; i < 5; i++) {
      mockMvc.perform(post(ApiPaths.TOOLS_GENERATE))
        .andExpect(status().isOk());
    }
  }
}
