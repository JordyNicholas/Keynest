package dev.keynest.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keynest.tools.demo")
public class ToolsDemoProperties {
  private boolean enabled = false;
  private String keyHex = "";
  private String keyId = "demo-kid";

  
  public boolean getEnabled() {
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
