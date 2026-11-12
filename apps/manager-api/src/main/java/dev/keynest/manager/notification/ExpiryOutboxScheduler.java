package dev.keynest.manager.notification;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "keynest.notifications.scan-enabled", havingValue = "true")
class ExpiryOutboxScheduler {
  private final ExpiryOutbox outbox;

  ExpiryOutboxScheduler(ExpiryOutbox outbox) {
    this.outbox = outbox;
  }

  @Scheduled(fixedDelayString = "${keynest.notifications.scan-delay:PT5M}")
  void scan() {
    outbox.enqueueDue(Instant.now());
  }
}
