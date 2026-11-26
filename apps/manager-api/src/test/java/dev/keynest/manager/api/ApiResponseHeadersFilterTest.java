package dev.keynest.manager.api;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseHeadersFilterTest {

  @ParameterizedTest
  @EnumSource(value = DispatcherType.class, names = {"REQUEST", "ASYNC", "ERROR"})
  void appliesBeforeDownstreamCodeAndSurvivesResetAndCommit(DispatcherType dispatch) throws Exception {
    var request = new MockHttpServletRequest();
    request.setDispatcherType(dispatch);
    var response = new MockHttpServletResponse();
    new ApiResponseHeadersFilter().doFilter(request, response, (req, res) -> {
      var http = (HttpServletResponse) res;
      assertThat(http.getHeader("X-Frame-Options")).isEqualTo("DENY");
      http.reset();
      assertThat(http.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
      http.setStatus(503);
      http.setContentType("application/json");
      http.setHeader("Cache-Control", "no-store");
      http.setHeader("Retry-After", "60");
      http.getWriter().write("{}");
      http.flushBuffer();
    });
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getContentAsString()).isEqualTo("{}");
    assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(response.getHeader("Content-Security-Policy"))
      .isEqualTo("default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
  }

  @Test
  void forwardingAndTlsDoNotOptTheApiIntoHsts() throws Exception {
    var request = new MockHttpServletRequest();
    request.setSecure(true);
    request.addHeader("Forwarded", "proto=https");
    request.addHeader("X-Forwarded-Proto", "https");
    var response = new MockHttpServletResponse();
    new ApiResponseHeadersFilter().doFilter(request, response, (req, res) -> {});
    assertThat(response.getHeader("Strict-Transport-Security")).isNull();
  }
}
