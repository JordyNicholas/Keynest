package dev.keynest.manager.config;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class KeynestConfig {

    @Bean
    PasswordPolicy defaultPasswordPolicy() {
        return PasswordPolicy.defaultPolicy();
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
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:4200")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
