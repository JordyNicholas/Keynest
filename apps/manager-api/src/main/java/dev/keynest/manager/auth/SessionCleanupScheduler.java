package dev.keynest.manager.auth;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SessionCleanupScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(SessionCleanupScheduler.class);
  private final ExpiredSessionCleanup cleanup;

  SessionCleanupScheduler(ExpiredSessionCleanup cleanup) {
    this.cleanup = cleanup;
  }

  @Scheduled(initialDelayString = "${keynest.session-cleanup.delay-seconds:300}",
    fixedDelayString = "${keynest.session-cleanup.delay-seconds:300}", timeUnit = TimeUnit.SECONDS)
  void clean() {
    try {
      cleanup.cleanBatch();
    } catch (RuntimeException failure) {
      // Do not emit database exception messages, statements, parameters or row metadata.
      LOG.warn("Expired-session cleanup failed; the next scheduled batch will retry.");
    }
  }
}
