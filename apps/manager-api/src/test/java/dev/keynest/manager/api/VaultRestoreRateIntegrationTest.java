package dev.keynest.manager.api;

import dev.keynest.manager.auth.AuthService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"keynest.rate-limit.enabled=true", "keynest.rate-limit.limit=2"})
@AutoConfigureMockMvc
class VaultRestoreRateIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  dev.keynest.manager.testing.TestAccounts accounts;

  private static final AtomicInteger IPS = new AtomicInteger();
  @Autowired
  MockMvc mvc;
  @Autowired
  AuthService auth;
  @Test
  void bothRoutesAreThrottledAndAllRejectionsAndMethodsRemainNoStore() throws Exception {
    String bearer = "Bearer " + accounts
      .register("restore-rate-" + UUID.randomUUID() + "@example.invalid", "Synthetic master passphrase!42").token();
    for (String path : new String[]{ApiPaths.VAULT_RESTORE_PREVIEW, ApiPaths.VAULT_RESTORE_APPLY}) {
      String ip = "198.18.0." + IPS.incrementAndGet();
      for (int attempt = 0; attempt < 3; attempt++) {
        var result = mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
          .content("{}").with(request -> {
            request.setRemoteAddr(ip);
            return request;
          })).andExpect(header().string("Cache-Control", "no-store"));
        if (attempt < 2)
          result.andExpect(status().isBadRequest());
        else
          result.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
      }
      mvc.perform(get(path)).andExpect(status().isUnauthorized())
        .andExpect(header().string("Cache-Control", "no-store"));
      mvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Cache-Control", "no-store"));
    }
  }
}
