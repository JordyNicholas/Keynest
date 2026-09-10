package dev.keynest.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "keynest.cors")
public class CorsProperties {

    /**
     * Browser origins allowed to call {@code /api/**}.
     * Comma-separated via env {@code KEYNEST_CORS_ALLOWED_ORIGINS} under the docker profile.
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:4200"));

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
