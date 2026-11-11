package dev.keynest.manager.api;

import dev.keynest.manager.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterCapacityTest {

  @Test
  void failsClosedWhenActiveIdentityCapacityIsExhausted() throws Exception {
    RateLimitProperties properties = new RateLimitProperties();
    properties.setLimit(100);
    properties.setWindowSeconds(60);
    properties.setMaxEntries(1);
    RateLimitFilter filter = new RateLimitFilter(properties, new ClientIpResolver(properties));

    MockHttpServletResponse firstResponse = invoke(filter, "198.51.100.1");
    MockHttpServletResponse secondResponse = invoke(filter, "198.51.100.2");

    assertThat(firstResponse.getStatus()).isEqualTo(200);
    assertThat(secondResponse.getStatus()).isEqualTo(429);
    assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
    assertThat(secondResponse.getContentAsString()).isEqualTo("{\"error\":\"rate_limit_exceeded\"}");
  }

  private static MockHttpServletResponse invoke(RateLimitFilter filter, String remoteAddress) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", ApiPaths.AUTH_LOGIN);
    request.setRemoteAddr(remoteAddress);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
