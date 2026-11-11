package dev.keynest.manager.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Fixed-field HTTP outcomes only; this is not a durable or actor-attributed audit log. */
@Component
public class SecurityEventLogger {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityEventLogger.class);

  public enum Action {
    AUTH_REGISTER, AUTH_LOGIN, AUTH_LOGOUT, AUTH_ME,
    VAULT_LIST, VAULT_READ, VAULT_CREATE, VAULT_UPDATE, VAULT_DELETE, VAULT_REVEAL
  }

  public enum Outcome {
    SUCCESS, REJECTED, ERROR, OTHER
  }

  public void record(Action action, int status) {
    Outcome outcome = status >= 500 ? Outcome.ERROR
      : status >= 400 ? Outcome.REJECTED
      : status >= 200 && status < 300 ? Outcome.SUCCESS : Outcome.OTHER;
    LOG.info("security_event schema_version=1 event_id={} occurred_at={} action={} outcome={} http_status={}",
      UUID.randomUUID(), Instant.now(), action, outcome, status);
  }
}
