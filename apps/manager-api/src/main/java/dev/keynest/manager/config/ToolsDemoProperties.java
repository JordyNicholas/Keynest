package dev.keynest.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keynest.tools.demo")
public class ToolsDemoProperties {

    /** When false, {@code /tools/seal} and {@code /tools/open} reject. */
    private boolean enabled = false;

    /** 32-byte AES key as hex (64 hex chars). Only required when enabled. */
    private String keyHex = "";

    private String keyId = "demo-kid";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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
