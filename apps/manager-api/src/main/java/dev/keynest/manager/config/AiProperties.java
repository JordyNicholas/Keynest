package dev.keynest.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keynest.ai")
public class AiProperties {
  private boolean enabled;
  private String openaiModel = "";
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getOpenaiModel() { return openaiModel; }
  public void setOpenaiModel(String model) { this.openaiModel = model == null ? "" : model; }
}
