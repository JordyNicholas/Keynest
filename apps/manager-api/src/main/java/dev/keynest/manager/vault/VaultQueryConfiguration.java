package dev.keynest.manager.vault;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class VaultQueryConfiguration {
  @Bean
  Clock vaultQueryClock() { return Clock.systemUTC(); }
}
