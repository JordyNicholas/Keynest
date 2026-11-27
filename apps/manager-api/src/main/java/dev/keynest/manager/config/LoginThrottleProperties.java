package dev.keynest.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keynest.login-throttle")
public class LoginThrottleProperties {
  private int failureLimit = 5;
  private int windowSeconds = 60;
  private int cooldownSeconds = 30;
  private int maxEntries = 10_000;

  public int getFailureLimit() { return failureLimit; }
  public void setFailureLimit(int value) { failureLimit = value; }
  public int getWindowSeconds() { return windowSeconds; }
  public void setWindowSeconds(int value) { windowSeconds = value; }
  public int getCooldownSeconds() { return cooldownSeconds; }
  public void setCooldownSeconds(int value) { cooldownSeconds = value; }
  public int getMaxEntries() { return maxEntries; }
  public void setMaxEntries(int value) { maxEntries = value; }
}
