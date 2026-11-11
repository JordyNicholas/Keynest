package dev.keynest.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "keynest.rate-limit")
public class RateLimitProperties {

  private boolean enabled = true;
  private int limit = 60;
  private int windowSeconds = 60;
  private int maxEntries = 10_000;
  private int maxForwardedForEntries = 16;
  private List<String> trustedProxies = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  public int getWindowSeconds() {
    return windowSeconds;
  }

  public void setWindowSeconds(int windowSeconds) {
    this.windowSeconds = windowSeconds;
  }

  public int getMaxEntries() {
    return maxEntries;
  }

  public void setMaxEntries(int maxEntries) {
    this.maxEntries = maxEntries;
  }

  public int getMaxForwardedForEntries() {
    return maxForwardedForEntries;
  }

  public void setMaxForwardedForEntries(int maxForwardedForEntries) {
    this.maxForwardedForEntries = maxForwardedForEntries;
  }

  public List<String> getTrustedProxies() {
    return trustedProxies;
  }

  public void setTrustedProxies(List<String> trustedProxies) {
    this.trustedProxies = trustedProxies == null ? new ArrayList<>() : new ArrayList<>(trustedProxies);
  }
}
