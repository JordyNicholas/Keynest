package dev.keynest.manager.auth.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Retention only: never claims jobs, sends mail or activates an account. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class VerificationMaintenance {
  private static final Logger LOG = LoggerFactory.getLogger(VerificationMaintenance.class);
  private final VerificationStore store;
  VerificationMaintenance(VerificationStore store) {
    this.store = store;
  }

  @Scheduled(initialDelay = 60000, fixedDelay = 60000)
  void clean() {
    try {
      store.cleanBatch();
    } catch (RuntimeException failure) {
      LOG.warn("Verification maintenance unavailable; a later bounded batch will retry.");
    }
  }
}
