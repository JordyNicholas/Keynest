package dev.keynest.manager.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "keynest.session-cleanup")
public class SessionCleanupProperties {
  @Min(1) @Max(5000)
  private int batchSize = 500;
  @Min(10) @Max(86400)
  private int delaySeconds = 300;

  public int getBatchSize() { return batchSize; }
  public void setBatchSize(int value) { batchSize = value; }
  public int getDelaySeconds() { return delaySeconds; }
  public void setDelaySeconds(int value) { delaySeconds = value; }
}
