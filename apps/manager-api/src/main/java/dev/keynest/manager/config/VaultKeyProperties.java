package dev.keynest.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keynest.vault")
public class VaultKeyProperties {

    /** 32-byte AES key as hex (64 hex chars). */
    private String keyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private String keyId = "vault-v1";

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
}
