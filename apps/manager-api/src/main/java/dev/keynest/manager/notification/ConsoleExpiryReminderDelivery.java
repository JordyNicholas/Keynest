package dev.keynest.manager.notification;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simulates delivery in a local rehearsal. Enable only against disposable demo data. */
@Component
@ConditionalOnProperty(name = "keynest.notifications.console-demo-enabled", havingValue = "true")
class ConsoleExpiryReminderDelivery implements ExpiryReminderDispatcher.Delivery {
  private static final Logger LOG = LoggerFactory.getLogger(ConsoleExpiryReminderDelivery.class);

  @Override
  public ExpiryReminderDispatcher.Outcome deliver(UUID ownerId) {
    LOG.info("Expiry reminder delivery simulated");
    return ExpiryReminderDispatcher.Outcome.SENT;
  }
}
