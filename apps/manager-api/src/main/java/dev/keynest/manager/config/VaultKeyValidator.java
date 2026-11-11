package dev.keynest.manager.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HexFormat;

/** Fails application startup before an unsafe vault key can serve requests. */
@Component
public class VaultKeyValidator {

  private final VaultKeyProperties properties;
  private final Environment environment;

  public VaultKeyValidator(VaultKeyProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @PostConstruct
  void validate() {
    String keyHex = properties.getKeyHex();
    if (keyHex == null || keyHex.isBlank()) {
      throw new IllegalStateException("keynest.vault.key-hex is required");
    }

    byte[] key;
    try {
      key = HexFormat.of().parseHex(keyHex);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("keynest.vault.key-hex must be valid hexadecimal", exception);
    }
    try {
      if (key.length != 32) {
        throw new IllegalStateException("keynest.vault.key-hex must decode to 32 bytes");
      }
    } finally {
      Arrays.fill(key, (byte) 0);
    }

    String keyId = properties.getKeyId();
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalStateException("keynest.vault.key-id is required");
    }

    boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
    boolean demoKey = VaultKeyProperties.DEMO_KEY_HEX.equalsIgnoreCase(keyHex);
    if (demoKey && (production || !properties.isAllowDemoKey())) {
      throw new IllegalStateException(
              "the documented demo vault key is forbidden; configure KEYNEST_VAULT_KEY_HEX"
      );
    }
  }
}
