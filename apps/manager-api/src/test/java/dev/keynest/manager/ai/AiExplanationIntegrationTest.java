package dev.keynest.manager.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.security.SecurityEventLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "keynest.ai.enabled=true")
@AutoConfigureMockMvc
class AiExplanationIntegrationTest {
  private static final String BODY = "{\"providerId\":\"test\",\"policyId\":\"keynest.default.v1\"}";
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired AiCoreTest.Fake fake;
  private String bearer;

  @TestConfiguration
  static class Config {
    @Bean AiCoreTest.Fake fakeAiProvider() { return new AiCoreTest.Fake(); }
    @Bean @Primary AiAdmission fixedAiAdmission() {
      return new AiAdmission(java.time.Clock.fixed(java.time.Instant.parse("2026-11-11T12:00:00Z"), java.time.ZoneOffset.UTC));
    }
  }

  @BeforeEach
  void session() {
    bearer = "Bearer " + auth.register("ai-" + UUID.randomUUID() + "@example.com", "SyntheticMaster!42").token();
    fake.calls = 0;
    fake.failure = null;
    fake.output = "Use the selected character categories and length bounds.";
  }

  @Test
  void authenticatedExplanationIsNoStoreAndDoesNotLogCredentials() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
    var capture = new ListAppender<ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    try {
      mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", AiCoreTest.KEY)
          .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.providerId").value("test"))
        .andExpect(jsonPath("$.explanation").value(fake.output));
      assertThat(fake.calls).isEqualTo(1);
      assertThat(capture.list).hasSize(1);
      assertThat(capture.list.getFirst().getFormattedMessage()).contains("action=AI_POLICY_EXPLAIN outcome=SUCCESS")
        .doesNotContain(AiCoreTest.KEY, bearer, "keynest.default.v1", fake.output);
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }

  @Test
  void unauthenticatedRequestMakesNoProviderCall() throws Exception {
    mvc.perform(post(ApiPaths.AI_EXPLAIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
      .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    assertThat(fake.calls).isZero();
  }

  @Test
  void strictBoundedBodyAndDuplicateHeadersAreRejected() throws Exception {
    for (String body : new String[]{"{}", BODY + "{}", BODY.replace("}", ",\"password\":\"secret-sentinel\"}"),
      "{\"providerId\":\"test\",\"providerId\":\"other\",\"policyId\":\"keynest.default.v1\"}"}) {
      mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", AiCoreTest.KEY)
          .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AI_INVALID_REQUEST"));
    }
    mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", AiCoreTest.KEY)
        .contentType(MediaType.APPLICATION_JSON).content(" ".repeat(8193)))
      .andExpect(status().isPayloadTooLarge());
    mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", "first", "second")
        .contentType(MediaType.APPLICATION_JSON).content(BODY))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AI_CREDENTIAL_INVALID"));
    assertThat(fake.calls).isZero();
  }

  @Test
  void missingCredentialAndUnsupportedIdsMakeNoProviderCalls() throws Exception {
    mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(BODY))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AI_CREDENTIAL_REQUIRED"));
    for (String body : new String[]{BODY.replace("test", "unknown"), BODY.replace("keynest.default.v1", "private")}) {
      mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", AiCoreTest.KEY)
          .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    assertThat(fake.calls).isZero();
  }

  @Test
  void rateLimitAndProviderFailuresHaveStableNoStoreErrors() throws Exception {
    for (int i = 0; i < 5; i++) {
      mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", AiCoreTest.KEY)
          .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
    }
    mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", AiCoreTest.KEY)
        .contentType(MediaType.APPLICATION_JSON).content(BODY))
      .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("AI_RATE_LIMITED"));
    assertThat(fake.calls).isEqualTo(5);
  }

  @Test
  void upstreamDetailsAndCredentialReflectionAreNeverReturned() throws Exception {
    fake.failure = new IllegalStateException(AiCoreTest.KEY);
    mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", AiCoreTest.KEY)
        .contentType(MediaType.APPLICATION_JSON).content(BODY))
      .andExpect(status().isBadGateway()).andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(content().json("{\"error\":\"AI_UNAVAILABLE\"}"));
    fake.failure = null;
    fake.output = "echo " + AiCoreTest.KEY;
    mvc.perform(post(ApiPaths.AI_EXPLAIN).header("Authorization", bearer).header("X-Keynest-AI-Key", AiCoreTest.KEY)
        .contentType(MediaType.APPLICATION_JSON).content(BODY))
      .andExpect(status().isBadGateway()).andExpect(content().json("{\"error\":\"AI_INVALID_OUTPUT\"}"));
  }
}
