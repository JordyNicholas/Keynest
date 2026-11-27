package dev.keynest.manager.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.AuthSession;
import dev.keynest.manager.auth.AuthSessionRepository;
import dev.keynest.manager.vault.VaultService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"keynest.rate-limit.enabled=false"})
@AutoConfigureMockMvc
class SessionRevocationHttpIntegrationTest {
  @Autowired MockMvc http;
  @Autowired AuthService auth;
  @Autowired AuthSessionRepository sessions;
  @Autowired VaultService vault;
  private static final String PASSWORD = "Synthetic account passphrase!42";

  private AuthService.AuthResponse register() { return auth.register(UUID.randomUUID() + "@example.invalid", PASSWORD); }
  private String bearer(AuthService.AuthResponse session) { return "Bearer " + session.token(); }

  @Test
  void revokesEveryOwnerSessionIncludingCurrentPreservesOtherOwnerAndVaultAndAllowsFreshSignIn() throws Exception {
    var first = register();var second = auth.login(first.email(), PASSWORD);var other = register();
    var owner = auth.requireUser(bearer(first));
    var item = vault.create(owner, new VaultService.CreateVaultItemRequest("Synthetic revocation item", null, null, null, true, null, 90, 14));
    String secret = vault.reveal(owner, item.id()).password();
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", bearer(first)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"accountPassword\":\"" + PASSWORD + "\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"))
      .andExpect(header().string("Cache-Control", "no-store"));
    for (var session : new AuthService.AuthResponse[]{first, second}) {
      http.perform(post(ApiPaths.AUTH_ME).header("Authorization", bearer(session))).andExpect(status().isUnauthorized());
    }
    assertThat(auth.requireUser(bearer(other)).getId()).isEqualTo(other.userId());
    var fresh = auth.login(first.email(), PASSWORD);
    assertThat(fresh.userId()).isEqualTo(first.userId());
    assertThat(secret.equals(vault.reveal(auth.requireUser(bearer(fresh)), item.id()).password())).as("Vault secret remains unchanged").isTrue();
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", bearer(first)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"accountPassword\":\"" + PASSWORD + "\"}"))
      .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    assertThat(auth.requireUser(bearer(fresh)).getId()).isEqualTo(first.userId());
  }

  @Test
  void wrongPasswordAndForeignIdentityFieldsWriteNothing() throws Exception {
    var first = register();
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", bearer(first)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"accountPassword\":\"wrong\"}"))
      .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_VERIFICATION_FAILED"));
    for (String body : new String[]{"{\"accountPassword\":\"" + PASSWORD + "\",\"userId\":\"" + UUID.randomUUID() + "\"}",
      "{\"accountPassword\":\"a\",\"accountPassword\":\"b\"}", "{}", "[]", "{\"accountPassword\":null}",
      "{\"accountPassword\":\"\"}", "{\"accountPassword\":\"a\"} {}", "{not json"}) {
      http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", bearer(first)).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
    }
    assertThat(auth.requireUser(bearer(first)).getId()).isEqualTo(first.userId());
  }

  @Test
  void anonymousInvalidAndExpiredSessionsCannotReachProofAndAllMethodsAreNoStore() throws Exception {
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).contentType(MediaType.APPLICATION_JSON).content("not json"))
      .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", "Bearer invalid").contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isUnauthorized());
    var first = register();
    var current = sessions.findAll().stream().filter(row -> first.userId().equals(row.getUserId())).findFirst().orElseThrow();
    String digest = current.getTokenDigest();sessions.delete(current);
    sessions.save(new AuthSession(UUID.randomUUID(), first.userId(), digest, Instant.now().minusSeconds(1), Instant.now().minusSeconds(60)));
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", bearer(first)).contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isUnauthorized());
    var active = register();
    http.perform(get(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", bearer(active)))
      .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Cache-Control", "no-store"));
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL + "?userId=ignored").header("Authorization", bearer(active))
        .contentType(MediaType.APPLICATION_JSON).content("{\"accountPassword\":\"" + PASSWORD + "\"}"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void boundsBodiesWithKnownOrUnknownLengthWithoutRevoking() throws Exception {
    var first = register();
    String oversized = " ".repeat(SessionRevocationRequest.MAX_BYTES + 1);
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", bearer(first))
        .contentType(MediaType.APPLICATION_JSON).content(oversized))
      .andExpect(status().isPayloadTooLarge()).andExpect(header().string("Cache-Control", "no-store"));
    http.perform(post(ApiPaths.AUTH_LOGOUT_ALL).header("Authorization", bearer(first))
        .contentType(MediaType.APPLICATION_JSON).content(oversized).with(request -> {
          var unknown = new org.springframework.mock.web.MockHttpServletRequest("POST", ApiPaths.AUTH_LOGOUT_ALL) {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
          };
          unknown.setContentType("application/json");unknown.setContent(oversized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
          unknown.addHeader("Authorization", bearer(first));return unknown;
        }))
      .andExpect(status().isPayloadTooLarge());
    assertThat(auth.requireUser(bearer(first)).getId()).isEqualTo(first.userId());
  }
}
