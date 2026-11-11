package dev.keynest.manager.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultKeyValidatorTest {

  @Test
  void acceptsExplicitNonDemoKey() {
    VaultKeyProperties properties = properties("ab".repeat(32), false);

    assertThatCode(() -> validator(properties).validate()).doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingKey() {
    VaultKeyProperties properties = properties("", false);

    assertThatThrownBy(() -> validator(properties).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("key-hex is required");
  }

  @Test
  void rejectsInvalidKeyLength() {
    VaultKeyProperties properties = properties("ab", false);

    assertThatThrownBy(() -> validator(properties).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes");
  }

  @Test
  void rejectsDemoKeyWithoutExplicitDevelopmentOptIn() {
    VaultKeyProperties properties = properties(VaultKeyProperties.DEMO_KEY_HEX, false);

    assertThatThrownBy(() -> validator(properties).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("demo vault key is forbidden");
  }

  @Test
  void acceptsDemoKeyWithExplicitDevelopmentOptIn() {
    VaultKeyProperties properties = properties(VaultKeyProperties.DEMO_KEY_HEX, true);

    assertThatCode(() -> validator(properties).validate()).doesNotThrowAnyException();
  }

  @Test
  void productionProfileRejectsDemoKeyEvenWhenOptedIn() {
    VaultKeyProperties properties = properties(VaultKeyProperties.DEMO_KEY_HEX, true);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");

    assertThatThrownBy(() -> new VaultKeyValidator(properties, environment).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("demo vault key is forbidden");
  }

  private static VaultKeyValidator validator(VaultKeyProperties properties) {
    return new VaultKeyValidator(properties, new MockEnvironment());
  }

  private static VaultKeyProperties properties(String keyHex, boolean allowDemoKey) {
    VaultKeyProperties properties = new VaultKeyProperties();
    properties.setKeyHex(keyHex);
    properties.setKeyId("test-vault-key");
    properties.setAllowDemoKey(allowDemoKey);
    return properties;
  }
}
