package dev.keynest.manager.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.vault.VaultItem;
import dev.keynest.manager.vault.VaultItemRepository;
import dev.keynest.manager.vault.VaultService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"keynest.rate-limit.enabled=true", "keynest.rate-limit.limit=2"})
@AutoConfigureMockMvc
class VaultExportHttpIntegrationTest {
  private static final String ALL = "{\"destination\":\"keynest\",\"scope\":\"all\"}";
  private static final AtomicInteger IPS = new AtomicInteger();
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired VaultService vault;
  @Autowired VaultItemRepository items;
  private String bearer;
  private UserAccount owner;

  @BeforeEach void setup() {
    bearer = "Bearer " + auth.register("http-export-" + UUID.randomUUID() + "@example.invalid", "Synthetic account password!42").token();
    owner = auth.requireUser(bearer);
    vault.create(owner, new VaultService.CreateVaultItemRequest("Synthetic item", null, null, null, true, null, null, null));
  }
  private MockHttpServletRequestBuilder request(String path, String body) {
    int ip = IPS.incrementAndGet();
    return post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body)
      .with(r -> { r.setRemoteAddr("198.51.100." + ip);return r; });
  }

  @Test void malformedAndUnsupportedBodiesAreSanitizedWithoutLoggingCredentialsOrMetadata() throws Exception {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    var capture = new ListAppender<ILoggingEvent>();capture.start();root.addAppender(capture);
    try {
      for (String body : new String[]{"{}", ALL + "{}", ALL.replace("}", ",\"password\":\"body-secret-sentinel\"}"),
        "{\"destination\":\"keynest\",\"scope\":\"all\",\"scope\":\"selected\"}",
        "{\"destination\":\"browser\",\"scope\":\"all\"}", "{\"destination\":\"keynest\",\"scope\":\"selected\",\"itemIds\":[]}"}) {
        mvc.perform(request(ApiPaths.VAULT_EXPORT_PREVIEW, body)).andExpect(status().isBadRequest())
          .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value("EXPORT_INVALID_REQUEST"));
      }
      mvc.perform(request(ApiPaths.VAULT_EXPORT_DOWNLOAD, ALL.replace("}", ",\"snapshot\":\"short\",\"accountPassword\":\"credential-secret-sentinel\",\"backupPassphrase\":\"Synthetic backup passphrase\"}")))
        .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Content-Disposition"));
      for (ILoggingEvent event : capture.list) {
        assertThat(event.getFormattedMessage()).doesNotContain("body-secret-sentinel", "credential-secret-sentinel", bearer, "Synthetic item");
      }
    } finally { root.detachAppender(capture);capture.stop(); }
  }

  @Test void requestAndSelectionBoundsAreNoStoreAndNeverBecomePartialExports() throws Exception {
    mvc.perform(request(ApiPaths.VAULT_EXPORT_PREVIEW, " ".repeat(VaultExportRequest.MAX_BYTES + 1)))
      .andExpect(status().isPayloadTooLarge()).andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.code").value("EXPORT_REQUEST_TOO_LARGE"));
    var seeded = new ArrayList<VaultItem>();Instant now = Instant.now();
    for (int i = 0; i < 500; i++) {
      seeded.add(new VaultItem(UUID.randomUUID(), owner.getId(), "Synthetic bounded row", null, null, new byte[64],
        "keynest.default.v1", 1, null, now, 14, now, now));
    }
    items.saveAllAndFlush(seeded);
    mvc.perform(request(ApiPaths.VAULT_EXPORT_PREVIEW, ALL)).andExpect(status().isPayloadTooLarge())
      .andExpect(jsonPath("$.code").value("EXPORT_LIMIT_EXCEEDED"));
  }

  @Test void limiterAndUnsupportedMethodsKeepTheSameCacheBoundary() throws Exception {
    int ip = IPS.incrementAndGet();
    for (int i = 0; i < 3; i++) {
      var result = mvc.perform(post(ApiPaths.VAULT_EXPORT_PREVIEW).header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON).content(ALL).with(r -> { r.setRemoteAddr("203.0.113." + ip);return r; }))
        .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache"));
      if (i < 2) result.andExpect(status().isOk());else result.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }
    mvc.perform(get(ApiPaths.VAULT_EXPORT_DOWNLOAD)).andExpect(status().isUnauthorized())
      .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get(ApiPaths.VAULT_EXPORT_DOWNLOAD).header("Authorization", bearer)).andExpect(status().isMethodNotAllowed())
      .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test void missingAndAmbiguousAuthenticationCannotReadAPreview() throws Exception {
    mvc.perform(post(ApiPaths.VAULT_EXPORT_PREVIEW).contentType(MediaType.APPLICATION_JSON).content(ALL))
      .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EXPORT_SESSION_REQUIRED"));
    mvc.perform(post(ApiPaths.VAULT_EXPORT_PREVIEW).header("Authorization", bearer, bearer).contentType(MediaType.APPLICATION_JSON).content(ALL))
      .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
  }
}
