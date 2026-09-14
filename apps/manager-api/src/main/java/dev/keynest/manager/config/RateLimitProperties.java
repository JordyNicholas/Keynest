package dev.keynest.manager.config;

public class RateLimitProperties {
  private Boolean enabled = true;
  private Integer limit = 60;
  private Integer windowSeconds = 60;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public Integer getWindowSeconds() {
    return windowSeconds;
  }

  public void setWindowSeconds(Integer windowSeconds) {
    this.windowSeconds = windowSeconds;
  }
}
