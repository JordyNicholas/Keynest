package dev.keynest.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keynest.vault")
public class VaultKeyProperties {

    public static final String DEMO_KEY_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /** 32-byte AES key as hex (64 hex chars). */
    private String keyHex = DEMO_KEY_HEX;
    private String keyId = "vault-v1";
    private boolean allowDemoKey;

    public String getKeyHex() {
        return keyHex;
    }

    public void setKeyHex(String keyHex) {
        this.keyHex = keyHex;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public boolean isAllowDemoKey() {
        return allowDemoKey;
    }

    public void setAllowDemoKey(boolean allowDemoKey) {
        this.allowDemoKey = allowDemoKey;
    }
}
