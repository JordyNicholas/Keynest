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

    MockHttpServletResponse firstResponse = invoke(filter, ApiPaths.AUTH_LOGIN, "198.51.100.1");
    MockHttpServletResponse secondResponse = invoke(filter, ApiPaths.AUTH_LOGIN, "198.51.100.2");

    assertThat(firstResponse.getStatus()).isEqualTo(200);
    assertThat(secondResponse.getStatus()).isEqualTo(429);
    assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
    assertThat(secondResponse.getContentAsString()).isEqualTo("{\"error\":\"rate_limit_exceeded\"}");
  }

  @Test
  void passwordToolsLimitEachRouteIndependently() throws Exception {
    RateLimitProperties properties = new RateLimitProperties();
    properties.setLimit(1);
    RateLimitFilter filter = new RateLimitFilter(properties, new ClientIpResolver(properties));

    String client = "198.51.100.1";
    for (String path : new String[] {ApiPaths.TOOLS_HASH_PASSWORD, ApiPaths.TOOLS_VERIFY_PASSWORD}) {
      assertThat(invoke(filter, path, client).getStatus()).isEqualTo(200);
      MockHttpServletResponse limited = invoke(filter, path, client);
      assertThat(limited.getStatus()).isEqualTo(429);
      assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
    }
  }

  private static MockHttpServletResponse invoke(RateLimitFilter filter, String path, String remoteAddress)
    throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr(remoteAddress);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
