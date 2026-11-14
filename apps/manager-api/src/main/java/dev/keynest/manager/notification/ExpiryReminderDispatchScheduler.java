package dev.keynest.manager.notification;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "keynest.notifications.dispatch-enabled", havingValue = "true")
class ExpiryReminderDispatchScheduler {
  private final ExpiryReminderDispatcher dispatcher;

  ExpiryReminderDispatchScheduler(JdbcTemplate jdbc, ExpiryReminderClaims claims,
                                 ExpiryReminderTiming timing, ExpiryReminderDispatcher.Delivery delivery) {
    dispatcher = new ExpiryReminderDispatcher(jdbc, claims, timing, Clock.systemUTC(), delivery);
  }

  @Scheduled(initialDelayString = "${keynest.notifications.dispatch-delay:PT1M}",
             fixedDelayString = "${keynest.notifications.dispatch-delay:PT1M}")
  void dispatch() {
    dispatcher.dispatchBatch();
  }
}
