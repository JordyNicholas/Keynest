package dev.keynest.manager;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.security.SecurityEventLogger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"keynest.rate-limit.limit=1"})
@AutoConfigureMockMvc
class SecurityEventIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void capturesAuthenticationAndLimiterRejectionWithoutSensitiveInputs() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
    var capture = new ListAppender<ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        mockMvc.perform(post(ApiPaths.AUTH_LOGIN).contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer header-secret-sentinel")
            .queryParam("key", "query-secret-sentinel")
            .content("{\"email\":\"email-secret-sentinel@example.invalid\",\"password\":\"password-secret-sentinel\"}")
            .with(request -> { request.setRemoteAddr("192.0.2.92"); return request; }))
          .andExpect(attempt == 0 ? status().isUnauthorized() : status().isTooManyRequests());
      }
      assertThat(capture.list).hasSize(2);
      assertThat(capture.list.get(0).getFormattedMessage()).contains("action=AUTH_LOGIN outcome=REJECTED http_status=401");
      assertThat(capture.list.get(1).getFormattedMessage()).contains("action=AUTH_LOGIN outcome=REJECTED http_status=429");
      for (var event : capture.list) {
        assertThat(event.getFormattedMessage()).doesNotContain("sentinel", "192.0.2.92", "Authorization");
        assertThat(event.getThrowableProxy()).isNull();
      }
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }
}
