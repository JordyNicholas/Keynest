package dev.keynest.manager.config;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.lifecycle.PasswordStandards;
import dev.keynest.policy.schema.PolicyRegistry;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.PasswordHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({
  VaultKeyProperties.class,
  CorsProperties.class,
  ToolsDemoProperties.class,
  RateLimitProperties.class,
  LoginThrottleProperties.class,
  SessionCleanupProperties.class,
  AiProperties.class
})
public class KeynestConfig {

    @Bean
    PolicyRegistry policyRegistry() {
        return PolicyRegistry.withDefaults();
    }

    @Bean
    PasswordStandards passwordStandards(PolicyRegistry policyRegistry) {
        return new PasswordStandards(policyRegistry);
    }

    @Bean
    PasswordPolicy defaultPasswordPolicy(PolicyRegistry policyRegistry) {
        return policyRegistry.runtime(PasswordPolicy.DEFAULT_POLICY_ID);
    }

    @Bean
    PasswordGenerator passwordGenerator() {
        return new PasswordGenerator();
    }

    @Bean
    KeynestSeal keynestSeal() {
        return new KeynestSeal();
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new PasswordHasher();
    }

    @Bean
    UrlBasedCorsConfigurationSource apiCorsConfiguration(CorsProperties corsProperties) {
        var configuration = new CorsConfiguration().applyPermitDefaultValues();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ApiPaths.API_PATTERN, configuration);
        return source;
    }
}
