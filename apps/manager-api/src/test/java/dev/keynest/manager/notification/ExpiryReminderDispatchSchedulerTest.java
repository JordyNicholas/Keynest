package dev.keynest.manager.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class ExpiryReminderDispatchSchedulerTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
    .withUserConfiguration(ExpiryReminderDispatchScheduler.class)
    .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
    .withBean(ExpiryReminderClaims.class, () -> mock(ExpiryReminderClaims.class))
    .withBean(ExpiryReminderTiming.class, () -> mock(ExpiryReminderTiming.class));

  @Test
  void schedulingIsOffByDefault() {
    context.run(result -> assertThat(result).doesNotHaveBean(ExpiryReminderDispatchScheduler.class));
  }

  @Test
  void enabledSchedulingRequiresAnExplicitDeliveryAdapter() {
    context.withPropertyValues("keynest.notifications.dispatch-enabled=true")
      .run(result -> assertThat(result).hasFailed());
  }

  @Test
  void enabledSchedulingStartsWithAnAdapter() {
    context.withPropertyValues("keynest.notifications.dispatch-enabled=true")
      .withBean(ExpiryReminderDispatcher.Delivery.class,
        () -> ownerId -> ExpiryReminderDispatcher.Outcome.INELIGIBLE)
      .run(result -> assertThat(result).hasSingleBean(ExpiryReminderDispatchScheduler.class));
  }
}
