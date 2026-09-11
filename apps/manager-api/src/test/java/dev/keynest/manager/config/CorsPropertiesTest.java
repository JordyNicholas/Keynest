package dev.keynest.manager.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "keynest.cors.allowed-origins=https://app.example.com,http://localhost:4200"
        })
class CorsPropertiesTest {

    @Autowired private CorsProperties corsProperties;

    @Test
    void bindsCommaSeparatedOrigins() {
        assertThat(corsProperties.getAllowedOrigins())
                .containsExactly("https://app.example.com", "http://localhost:4200");
    }
}
