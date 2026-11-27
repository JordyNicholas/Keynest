package dev.keynest.manager.auth;

import dev.keynest.manager.config.SessionCleanupProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One bounded batch; expiry enforcement remains in authentication, independent of cleanup. */
@Service
public class ExpiredSessionCleanup {
  private final AuthSessionRepository sessions;
  private final Clock clock;
  private final int batchSize;

  public ExpiredSessionCleanup(AuthSessionRepository sessions, Clock clock, SessionCleanupProperties properties) {
    this.sessions = sessions;
    this.clock = clock;
    batchSize = properties.getBatchSize();
  }

  @Transactional(timeout = 10)
  public int cleanBatch() {
    Instant cutoff = clock.instant();
    var ids = sessions.findExpiredIds(cutoff, PageRequest.of(0, batchSize));
    // Never issue an empty IN clause or an unbounded delete; recheck expiry at deletion.
    return ids.isEmpty() ? 0 : sessions.deleteExpiredIds(ids, cutoff);
  }
}
