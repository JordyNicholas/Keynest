package dev.keynest.manager.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.keynest.manager.security.SecurityEventLogger;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SecurityEventFilterTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
  private ListAppender<ILoggingEvent> capture;
  private SecurityEventFilter filter;

  @BeforeEach
  void captureLogs() {
    capture = new ListAppender<>();
    capture.start();
    logger.addAppender(capture);
    filter = new SecurityEventFilter(new SecurityEventLogger());
  }

  @AfterEach
  void detachCapture() {
    logger.detachAppender(capture);
    capture.stop();
  }

  @ParameterizedTest
  @CsvSource({
    "POST,/api/v1/auth/register,AUTH_REGISTER", "POST,/api/v1/auth/login,AUTH_LOGIN",
    "POST,/api/v1/auth/logout,AUTH_LOGOUT", "POST,/api/v1/auth/me,AUTH_ME",
    "GET,/api/v1/vault/items,VAULT_LIST", "POST,/api/v1/vault/items,VAULT_CREATE",
    "GET,/api/v1/vault/items/11111111-1111-1111-1111-111111111111,VAULT_READ",
    "PUT,/api/v1/vault/items/11111111-1111-1111-1111-111111111111,VAULT_UPDATE",
    "DELETE,/api/v1/vault/items/11111111-1111-1111-1111-111111111111,VAULT_DELETE",
    "POST,/api/v1/vault/items/11111111-1111-1111-1111-111111111111/reveal,VAULT_REVEAL"
  })
  void mapsOnlyFixedActionsAndOmitsRequestData(String method, String path, String action) throws Exception {
    var request = new MockHttpServletRequest(method, path);
    request.addHeader("Authorization", "Bearer header-secret-sentinel");
    request.setQueryString("key=query-secret-sentinel");
    request.setRemoteAddr("192.0.2.92");
    request.setContent("body-secret-sentinel".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> res.getWriter().write("response-secret-sentinel"));

    assertThat(capture.list).hasSize(1);
    String message = capture.list.getFirst().getFormattedMessage();
    assertThat(message).startsWith("security_event schema_version=1 event_id=")
      .contains(" occurred_at=", " action=" + action + " outcome=SUCCESS http_status=200")
      .doesNotContain("sentinel", "192.0.2.92", path, "\n", "\r");
    assertThat(capture.list.getFirst().getThrowableProxy()).isNull();
  }

  @Test
  void classifiesDeniedAndUnexpectedFailuresWithoutExceptionText() throws Exception {
    filter.doFilter(new MockHttpServletRequest("POST", ApiPaths.AUTH_LOGIN), new MockHttpServletResponse(),
      (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401));
    assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("POST", ApiPaths.AUTH_LOGIN),
      new MockHttpServletResponse(), (req, res) -> { throw new ServletException("exception-secret-sentinel"); }))
      .isInstanceOf(ServletException.class);
    assertThat(capture.list).hasSize(2);
    assertThat(capture.list.get(0).getFormattedMessage()).contains("outcome=REJECTED http_status=401");
    assertThat(capture.list.get(1).getFormattedMessage()).contains("outcome=ERROR http_status=500")
      .doesNotContain("sentinel");
    assertThat(capture.list.get(1).getThrowableProxy()).isNull();
  }

  @Test
  void ignoresUnknownToolsAndUnsupportedMethods() throws Exception {
    for (String path : new String[]{ApiPaths.TOOLS_GENERATE, "/unknown-secret-sentinel", ApiPaths.VAULT_ITEMS + "/not-a-uuid"}) {
      filter.doFilter(new MockHttpServletRequest("POST", path), new MockHttpServletResponse(), (req, res) -> {});
    }
    filter.doFilter(new MockHttpServletRequest("OPTIONS", ApiPaths.AUTH_LOGIN), new MockHttpServletResponse(), (req, res) -> {});
    assertThat(capture.list).isEmpty();
  }

  @Test
  void loggerFailureDoesNotChangeApplicationResult() throws Exception {
    var broken = mock(SecurityEventLogger.class);
    doThrow(new IllegalStateException("sink-secret-sentinel")).when(broken).record(any(), anyInt());
    var response = new MockHttpServletResponse();
    new SecurityEventFilter(broken).doFilter(new MockHttpServletRequest("POST", ApiPaths.AUTH_LOGIN), response,
      (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401));
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(capture.list).isEmpty();
  }
}
