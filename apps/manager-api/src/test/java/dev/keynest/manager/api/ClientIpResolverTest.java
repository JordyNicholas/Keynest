package dev.keynest.manager.api;

import dev.keynest.manager.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

  @Test
  void ignoresSpoofedForwardingHeaderFromUntrustedPeer() {
    ClientIpResolver resolver = resolver(List.of());
    MockHttpServletRequest request = request("203.0.113.9", "198.51.100.44");

    assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void resolvesClientThroughOneTrustedProxy() {
    ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"));
    MockHttpServletRequest request = request("10.2.3.4", "198.51.100.44");

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.44");
  }

  @Test
  void walksMultipleTrustedProxyHopsFromRightToLeft() {
    ClientIpResolver resolver = resolver(List.of("10.0.0.0/8", "192.0.2.10/32"));
    MockHttpServletRequest request = request(
      "10.2.3.4",
      "203.0.113.55, 198.51.100.99, 192.0.2.10"
    );

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.99");
  }

  @Test
  void supportsIpv6Cidrs() {
    ClientIpResolver resolver = resolver(List.of("2001:db8:abcd::/48"));
    MockHttpServletRequest request = request("2001:db8:abcd::5", "2001:db8:ffff::7");

    assertThat(resolver.resolve(request)).isEqualTo("2001:db8:ffff:0:0:0:0:7");
  }

  @Test
  void malformedOrOversizedChainFallsBackToPeer() {
    RateLimitProperties properties = properties(List.of("10.0.0.0/8"));
    properties.setMaxForwardedForEntries(2);
    ClientIpResolver resolver = new ClientIpResolver(properties);

    assertThat(resolver.resolve(request("10.0.0.1", "198.51.100.4, not-an-ip")))
      .isEqualTo("10.0.0.1");
    assertThat(resolver.resolve(request("10.0.0.1", "198.51.100.4, 192.0.2.2, 192.0.2.3")))
      .isEqualTo("10.0.0.1");
  }

  @Test
  void invalidTrustedProxyFailsAtStartup() {
    RateLimitProperties properties = properties(List.of("proxy.example.com/24"));

    assertThatThrownBy(() -> new ClientIpResolver(properties))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("invalid trusted proxy");
  }

  @Test
  void allAddressTrustedProxyRangeIsForbidden() {
    RateLimitProperties properties = properties(List.of("0.0.0.0/0"));

    assertThatThrownBy(() -> new ClientIpResolver(properties))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("all-address trusted proxy ranges are forbidden");
  }

  private static ClientIpResolver resolver(List<String> trustedProxies) {
    return new ClientIpResolver(properties(trustedProxies));
  }

  private static RateLimitProperties properties(List<String> trustedProxies) {
    RateLimitProperties properties = new RateLimitProperties();
    properties.setTrustedProxies(trustedProxies);
    return properties;
  }

  private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddress);
    request.addHeader("X-Forwarded-For", forwardedFor);
    return request;
  }
}
