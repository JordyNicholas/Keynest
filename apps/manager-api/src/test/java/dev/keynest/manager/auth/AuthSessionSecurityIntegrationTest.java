package dev.keynest.manager.auth;

import com.jayway.jsonpath.JsonPath;
import dev.keynest.manager.api.ApiPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSessionSecurityIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  AuthSessionRepository sessions;

  @Test
  void storesOnlyDigestAndAuthenticatesWithRawBearer() throws Exception {
    RegisteredSession registered = register();
    String digest = SessionTokenDigest.digest(registered.token());

    AuthSession persisted = sessions.findByTokenDigest(digest).orElseThrow();
    assertThat(persisted.getTokenDigest())
            .isEqualTo(digest)
            .isNotEqualTo(registered.token())
            .hasSize(64);

    mockMvc.perform(post(ApiPaths.AUTH_ME)
        .header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isOk());
  }

  @Test
  void rejectsUnknownBearer() throws Exception {
    mockMvc.perform(post(ApiPaths.AUTH_ME)
        .header("Authorization", "Bearer unknown-session-token"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsExpiredSession() throws Exception {
    RegisteredSession registered = register();
    String digest = SessionTokenDigest.digest(registered.token());
    AuthSession current = sessions.findByTokenDigest(digest).orElseThrow();
    sessions.delete(current);
    sessions.save(new AuthSession(
        UUID.randomUUID(),
        registered.userId(),
        digest,
        Instant.now().minusSeconds(1),
        Instant.now().minusSeconds(60)
    ));

    mockMvc.perform(post(ApiPaths.AUTH_ME)
        .header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsLegacySessionStoredAsRawToken() throws Exception {
    RegisteredSession registered = register();
    String digest = SessionTokenDigest.digest(registered.token());
    AuthSession current = sessions.findByTokenDigest(digest).orElseThrow();
    sessions.delete(current);
    sessions.save(new AuthSession(
        UUID.randomUUID(),
        registered.userId(),
        registered.token(),
        Instant.now().plusSeconds(60),
        Instant.now()
    ));

    mockMvc.perform(post(ApiPaths.AUTH_ME)
        .header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesSessionDigest() throws Exception {
    RegisteredSession registered = register();

    mockMvc.perform(post(ApiPaths.AUTH_LOGOUT)
        .header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isOk());

    assertThat(sessions.findByTokenDigest(SessionTokenDigest.digest(registered.token())))
            .isEmpty();
    mockMvc.perform(post(ApiPaths.AUTH_ME)
        .header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isUnauthorized());
  }

  private RegisteredSession register() throws Exception {
    String email = "session-" + UUID.randomUUID() + "@example.com";
    MvcResult result = mockMvc.perform(post(ApiPaths.AUTH_REGISTER)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"email":"%s","password":"MasterPassphrase!42"}
          """.formatted(email)))
      .andExpect(status().isOk())
      .andReturn();

    String response = result.getResponse().getContentAsString();
    return new RegisteredSession(
        JsonPath.read(response, "$.token"),
        UUID.fromString(JsonPath.read(response, "$.userId"))
    );
  }

  private record RegisteredSession(String token, UUID userId) {
  }
}
