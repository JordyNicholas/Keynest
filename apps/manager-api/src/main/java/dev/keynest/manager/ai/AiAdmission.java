package dev.keynest.manager.ai;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded process-local admission; no queue, credential or persistent user state. */
@Component
public class AiAdmission {
  private final Clock clock;
  private final Semaphore concurrent = new Semaphore(2);
  private final Map<UUID, Integer> users = new HashMap<>();
  private long window = Long.MIN_VALUE;
  private int count;

  public AiAdmission() { this(Clock.systemUTC()); }
  AiAdmission(Clock clock) { this.clock = clock; }

  public synchronized Lease acquire(UUID user) {
    long current = clock.millis() / 60_000;
    if (current != window) {
      window = current;
      count = 0;
      users.clear();
    }
    if (count >= 20 || users.getOrDefault(user, 0) >= 5) throw new AiFailure(AiFailure.Code.AI_RATE_LIMITED);
    if (!concurrent.tryAcquire()) throw new AiFailure(AiFailure.Code.AI_CAPACITY_LIMITED);
    count++;
    users.merge(user, 1, Integer::sum);
    return new Lease();
  }

  public final class Lease implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private Lease() {}
    @Override public void close() { if (closed.compareAndSet(false, true)) concurrent.release(); }
  }
}
