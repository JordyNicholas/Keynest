package dev.keynest.manager.config;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.lifecycle.PasswordStandards;
import dev.keynest.policy.schema.PolicyRegistry;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.PasswordHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({VaultKeyProperties.class, CorsProperties.class})
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
    WebMvcConfigurer corsConfigurer(CorsProperties corsProperties) {
        String[] origins = corsProperties.getAllowedOrigins().toArray(String[]::new);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
