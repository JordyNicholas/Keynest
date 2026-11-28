package dev.keynest.manager.auth;

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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSessionSecurityIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  AuthSessionRepository sessions;

  @Autowired
  UserAccountRepository users;

  @Autowired
  PasswordHasher passwordHasher;

  @Test
  void storesOnlyDigestAndAuthenticatesWithRawBearer() throws Exception {
    RegisteredSession registered = register();
    String digest = SessionTokenDigest.digest(registered.token());

    AuthSession persisted = sessions.findByTokenDigest(digest).orElseThrow();
    assertThat(persisted.getTokenDigest()).isEqualTo(digest).isNotEqualTo(registered.token()).hasSize(64);

    mockMvc.perform(post(ApiPaths.AUTH_ME).header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isOk());
  }

  @Test
  void rejectsRegistrationBelowTheMinimumByCodePoint() throws Exception {
    mockMvc.perform(post(ApiPaths.AUTH_REGISTER).contentType(MediaType.APPLICATION_JSON).content("""
      {"email":"short-password@example.com","password":"%s"}
      """.formatted("🔑".repeat(14)))).andExpect(status().isGone());
  }

  @Test
  void existingAccountCanSignInWithAShorterPassword() throws Exception {
    UUID userId = UUID.randomUUID();
    String email = "existing-" + userId + "@example.com";
    String password = "test fixture";
    users.save(dev.keynest.manager.testing.TestAccounts.account(userId, email,
      passwordHasher.hashPassword(password.toCharArray()), Instant.now()));

    mockMvc.perform(post(ApiPaths.AUTH_LOGIN).contentType(MediaType.APPLICATION_JSON).content("""
      {"email":"%s","password":"%s"}
      """.formatted(email, password))).andExpect(status().isOk())
      .andExpect(jsonPath("$.userId").value(userId.toString()));
  }

  @Test
  void rejectsMalformedRegistrationEmail() throws Exception {
    mockMvc.perform(post(ApiPaths.AUTH_REGISTER).contentType(MediaType.APPLICATION_JSON).content("""
      {"email":"not-an-email","password":"SyntheticTestPassword!42"}
      """)).andExpect(status().isGone());
  }

  @Test
  void rejectsSignInWithoutAPassword() throws Exception {
    RegisteredSession registered = register();
    mockMvc.perform(post(ApiPaths.AUTH_LOGIN).contentType(MediaType.APPLICATION_JSON).content("""
      {"email":"%s"}
      """.formatted(registered.email()))).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsUnknownBearer() throws Exception {
    mockMvc.perform(post(ApiPaths.AUTH_ME).header("Authorization", "Bearer unknown-session-token"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsExpiredSession() throws Exception {
    RegisteredSession registered = register();
    String digest = SessionTokenDigest.digest(registered.token());
    AuthSession current = sessions.findByTokenDigest(digest).orElseThrow();
    sessions.delete(current);
    sessions.save(new AuthSession(UUID.randomUUID(), registered.userId(), digest, Instant.now().minusSeconds(1),
      Instant.now().minusSeconds(60)));

    mockMvc.perform(post(ApiPaths.AUTH_ME).header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsLegacySessionStoredAsRawToken() throws Exception {
    RegisteredSession registered = register();
    String digest = SessionTokenDigest.digest(registered.token());
    AuthSession current = sessions.findByTokenDigest(digest).orElseThrow();
    sessions.delete(current);
    sessions.save(new AuthSession(UUID.randomUUID(), registered.userId(), registered.token(),
      Instant.now().plusSeconds(60), Instant.now()));

    mockMvc.perform(post(ApiPaths.AUTH_ME).header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesSessionDigest() throws Exception {
    RegisteredSession registered = register();

    mockMvc.perform(post(ApiPaths.AUTH_LOGOUT).header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isOk());

    assertThat(sessions.findByTokenDigest(SessionTokenDigest.digest(registered.token()))).isEmpty();
    mockMvc.perform(post(ApiPaths.AUTH_ME).header("Authorization", "Bearer " + registered.token()))
      .andExpect(status().isUnauthorized());
  }

  private RegisteredSession register() throws Exception {
    String email = "session-" + UUID.randomUUID() + "@example.com";
    var session = mockMvc.getDispatcherServlet().getWebApplicationContext()
      .getBean(dev.keynest.manager.testing.TestAccounts.class).register(email, "test passphrase");
    return new RegisteredSession(session.token(), session.userId(), email);
  }

  private record RegisteredSession(String token, UUID userId, String email) {
  }
}
