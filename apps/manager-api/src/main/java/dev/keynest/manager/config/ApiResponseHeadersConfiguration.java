package dev.keynest.manager.config;

import dev.keynest.manager.api.ApiResponseHeadersFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class ApiResponseHeadersConfiguration {

  @Bean
  FilterRegistrationBean<ApiResponseHeadersFilter> apiResponseHeaders() {
    var registration = new FilterRegistrationBean<>(new ApiResponseHeadersFilter());
    registration.setName("apiResponseHeaders");
    registration.addUrlPatterns("/*");
    registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
    registration.setAsyncSupported(true);
    // Before security events, export/restore bounds and rate-limit rejection paths.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }
}
