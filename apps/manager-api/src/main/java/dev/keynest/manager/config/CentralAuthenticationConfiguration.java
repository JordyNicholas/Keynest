package dev.keynest.manager.config;

import dev.keynest.manager.api.CentralAuthenticationInterceptor;
import dev.keynest.manager.api.CentralAuthenticationFilter;
import dev.keynest.manager.api.RouteAccessCatalog;
import dev.keynest.manager.auth.AuthService;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.handler.MappedInterceptor;

@Configuration
public class CentralAuthenticationConfiguration {
  private final CentralAuthenticationInterceptor authentication;
  public CentralAuthenticationConfiguration(CentralAuthenticationInterceptor authentication) { this.authentication = authentication; }

  @Bean
  FilterRegistrationBean<CentralAuthenticationFilter> centralAuthentication(AuthService auth, RouteAccessCatalog catalog) {
    var registration = new FilterRegistrationBean<>(new CentralAuthenticationFilter(auth, catalog));
    registration.addUrlPatterns("/*");registration.setAsyncSupported(true);
    registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
    return registration;
  }

  @Bean
  MappedInterceptor applicationHandlerAccess() {
    // Discovered by framework HandlerMappings too, not just MVC Java-config mappings.
    return new MappedInterceptor(new String[]{"/**"}, authentication);
  }
}
