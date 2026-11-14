package dev.keynest.manager.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ConsoleExpiryReminderDeliveryTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
    .withUserConfiguration(ConsoleExpiryReminderDelivery.class);

  @Test
  void consoleAdapterRequiresOptIn() {
    context.run(result -> assertThat(result).doesNotHaveBean(ExpiryReminderDispatcher.Delivery.class));
  }

  @Test
  void simulationLogsOnlyAGenericEvent(CapturedOutput output) {
    UUID owner = UUID.randomUUID();
    context.withPropertyValues("keynest.notifications.console-demo-enabled=true").run(result -> {
      var delivery = result.getBean(ExpiryReminderDispatcher.Delivery.class);
      assertThat(delivery.deliver(owner)).isEqualTo(ExpiryReminderDispatcher.Outcome.SENT);
    });
    assertThat(output.getAll()).contains("Expiry reminder delivery simulated").doesNotContain(owner.toString());
  }
}
