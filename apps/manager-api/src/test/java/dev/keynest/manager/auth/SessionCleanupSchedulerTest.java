package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import dev.keynest.manager.config.SessionCleanupProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class SessionCleanupSchedulerTest {
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SessionCleanupProperties.class)
  static class PropertiesConfiguration {}

  private final ApplicationContextRunner context = new ApplicationContextRunner()
    .withUserConfiguration(PropertiesConfiguration.class, SessionCleanupScheduler.class)
    .withBean(ExpiredSessionCleanup.class, () -> mock(ExpiredSessionCleanup.class));

  @Test
  void registersOneDelayedTaskWithReminderAndAiFeaturesDisabled() {
    context.withPropertyValues("keynest.notifications.scan-enabled=false",
      "keynest.notifications.dispatch-enabled=false", "keynest.ai.enabled=false").run(result -> {
        assertThat(result).hasNotFailed().hasSingleBean(SessionCleanupScheduler.class);
        var tasks = result.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
        assertThat(tasks).hasSize(1);
        var task = (org.springframework.scheduling.config.FixedDelayTask) tasks.iterator().next().getTask();
        assertThat(task.getIntervalDuration()).isEqualTo(java.time.Duration.ofSeconds(300));
        assertThat(task.getInitialDelayDuration()).isEqualTo(java.time.Duration.ofSeconds(300));
        verifyNoInteractions(result.getBean(ExpiredSessionCleanup.class));
      });
  }

  @ParameterizedTest
  @ValueSource(strings = {"batch-size=0", "batch-size=5001", "delay-seconds=9", "delay-seconds=86401"})
  void rejectsOutOfBoundsConfiguration(String setting) {
    context.withPropertyValues("keynest.session-cleanup." + setting)
      .run(result -> assertThat(result).hasFailed());
  }

  @Test
  void honorsOperatorDelayAndBatchBounds() {
    context.withPropertyValues("keynest.session-cleanup.batch-size=1", "keynest.session-cleanup.delay-seconds=10")
      .run(result -> {
        assertThat(result).hasNotFailed();
        var task = (org.springframework.scheduling.config.FixedDelayTask) result
          .getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().iterator().next().getTask();
        assertThat(task.getIntervalDuration()).isEqualTo(java.time.Duration.ofSeconds(10));
        assertThat(result.getBean(SessionCleanupProperties.class).getBatchSize()).isEqualTo(1);
      });
  }

  @Test
  @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
  void failureAllowsNextTickWithoutLoggingTheException(org.springframework.boot.test.system.CapturedOutput output) {
    var cleanup = mock(ExpiredSessionCleanup.class);
    when(cleanup.cleanBatch()).thenThrow(new IllegalStateException("sensitive database detail")).thenReturn(2);
    var scheduler = new SessionCleanupScheduler(cleanup);
    scheduler.clean();
    scheduler.clean();
    verify(cleanup, times(2)).cleanBatch();
    assertThat(output).contains("Expired-session cleanup failed;").doesNotContain("sensitive database detail");
  }
}
