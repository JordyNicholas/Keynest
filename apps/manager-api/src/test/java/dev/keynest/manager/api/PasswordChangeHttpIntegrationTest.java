package dev.keynest.manager.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.keynest.manager.auth.*;
import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.manager.vault.VaultService;
import dev.keynest.manager.vault.VaultItemRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "keynest.rate-limit.enabled=false")
@AutoConfigureMockMvc
class PasswordChangeHttpIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  dev.keynest.manager.testing.TestAccounts accounts;

  @Autowired
  MockMvc http;
  @Autowired
  AuthService auth;
  @Autowired
  AuthSessionRepository sessions;
  @Autowired
  UserAccountRepository users;
  @Autowired
  PasswordHashingService hashing;
  @Autowired
  VaultService vault;
  @Autowired
  VaultItemRepository items;
  @MockitoSpyBean
  PasswordChangeTransactions transactions;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String CURRENT = "Synthetic account password!42", NEXT = "New synthetic account password!42";
  private AuthService.AuthResponse register() {
    return accounts.register(UUID.randomUUID() + "@example.invalid", CURRENT);
  }
  private String bearer(AuthService.AuthResponse value) {
    return "Bearer " + value.token();
  }
  private String body(String current, String next) throws Exception {
    return JSON.createObjectNode().put("currentPassword", current).put("newPassword", next).toString();
  }

  @Test
  void replacesTheCurrentBearerRevokesOtherSessionsAndPreservesVaultBytes() throws Exception {
    var first = register();
    var second = auth.login(first.email(), CURRENT);
    var foreign = register();
    var user = auth.requireUser(bearer(first));
    var item = vault.create(user,
      new VaultService.CreateVaultItemRequest("Synthetic change item", null, null, null, true, null, 90, 14));
    byte[] envelope = items.findByIdAndUserId(item.id(), first.userId()).orElseThrow().getEnvelopeBytes().clone();
    String secret = vault.reveal(user, item.id()).password();
    var response = http
      .perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(first))
        .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEXT)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(first.userId().toString()))
      .andExpect(jsonPath("$.email").value(first.email())).andExpect(jsonPath("$.passwordHash").doesNotExist())
      .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
    String token = JSON.readTree(response.getContentAsByteArray()).path("token").textValue();
    assertThat(token != null && token.matches("[A-Za-z0-9_-]{43}") && !token.equals(first.token()))
      .as("fresh opaque bearer").isTrue();
    assertThat(auth.requireUser("Bearer " + token).getId()).isEqualTo(first.userId());
    for (var previous : new AuthService.AuthResponse[]{first, second}) {
      http.perform(post(ApiPaths.AUTH_ME).header("Authorization", bearer(previous)))
        .andExpect(status().isUnauthorized());
    }
    assertThat(auth.requireUser(bearer(foreign)).getId()).isEqualTo(foreign.userId());
    assertThat(
      Arrays.equals(envelope, items.findByIdAndUserId(item.id(), first.userId()).orElseThrow().getEnvelopeBytes()))
      .isTrue();
    assertThat(secret.equals(vault.reveal(auth.requireUser("Bearer " + token), item.id()).password())).isTrue();
    assertThatThrownBy(() -> auth.login(first.email(), CURRENT))
      .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(auth.login(first.email(), NEXT).userId()).isEqualTo(first.userId());
  }

  @Test
  void invalidAndWrongProofInputsCannotChangeTheCredential() throws Exception {
    var owner = register();
    http
      .perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(owner))
        .contentType(MediaType.APPLICATION_JSON).content(body("wrong", NEXT)))
      .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_VERIFICATION_FAILED"));
    for (String value : new String[]{"", " ".repeat(15), "🔑".repeat(14)}) {
      http
        .perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(owner))
          .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, value)))
        .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
    }
    for (String value : new String[]{"{}", "[]", "{not-json", body(CURRENT, NEXT) + " {}",
      "{\"currentPassword\":\"a\",\"newPassword\":\"b\",\"newPassword\":\"c\"}",
      JSON.createObjectNode().put("currentPassword", CURRENT).put("newPassword", NEXT)
        .put("userId", UUID.randomUUID().toString()).toString()}) {
      http.perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(owner))
        .contentType(MediaType.APPLICATION_JSON).content(value)).andExpect(status().isBadRequest());
    }
    assertThat(auth.login(owner.email(), CURRENT).userId()).isEqualTo(owner.userId());
  }

  @Test
  void preservesLegacyProofAndCountsTheNewMinimumByUnicodeCodePoint() throws Exception {
    UUID id = UUID.randomUUID();
    String email = id + "@example.invalid";
    users.save(dev.keynest.manager.testing.TestAccounts.account(id, email, hashing.hash("short"), Instant.now()));
    var session = auth.login(email, "short");
    String next = "🔑".repeat(15);
    http.perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(session))
      .contentType(MediaType.APPLICATION_JSON).content(body("short", next))).andExpect(status().isOk());
    assertThat(auth.login(email, next).userId()).isEqualTo(id);
    assertThatThrownBy(() -> auth.requireUser(bearer(session)))
      .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void requiresAnActiveSessionBeforeParsingAndDeniesUnsupportedMethodsWithNoStore() throws Exception {
    http.perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).contentType(MediaType.APPLICATION_JSON).content("not json"))
      .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    var owner = register();
    http.perform(get(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(owner)))
      .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Cache-Control", "no-store"));
    http.perform(post(ApiPaths.AUTH_PASSWORD_CHANGE + "?target=other").header("Authorization", bearer(owner))
      .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEXT))).andExpect(status().isBadRequest());
    auth.logout(bearer(owner));
    http.perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(owner))
      .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEXT))).andExpect(status().isUnauthorized());
  }

  @Test
  void boundsKnownAndUnknownLengthBodiesAndSanitizesUnexpectedFailures() throws Exception {
    var owner = register();
    String oversized = " ".repeat(PasswordChangeRequest.MAX_BYTES + 1);
    http
      .perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(owner))
        .contentType(MediaType.APPLICATION_JSON).content(oversized))
      .andExpect(status().isPayloadTooLarge()).andExpect(header().string("Cache-Control", "no-store"));
    http.perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(owner))
      .contentType(MediaType.APPLICATION_JSON).content(oversized).with(original -> {
        var unknown = new org.springframework.mock.web.MockHttpServletRequest("POST", ApiPaths.AUTH_PASSWORD_CHANGE) {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
          @Override
          public int getContentLength() {
            return -1;
          }
        };
        unknown.addHeader("Authorization", bearer(owner));
        unknown.setContentType("application/json");
        unknown.setContent(oversized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return unknown;
      })).andExpect(status().isPayloadTooLarge());
    doThrow(new IllegalStateException("sensitive-database-sentinel")).when(transactions).replace(eq(owner.userId()),
      anyString(), anyString(), anyString());
    String failure = http
      .perform(post(ApiPaths.AUTH_PASSWORD_CHANGE).header("Authorization", bearer(owner))
        .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEXT)))
      .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_UNAVAILABLE"))
      .andReturn().getResponse().getContentAsString();
    assertThat(failure).doesNotContain("sentinel", CURRENT, NEXT);
    assertThat(auth.requireUser(bearer(owner)).getId()).isEqualTo(owner.userId());
  }
}
