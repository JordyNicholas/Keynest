package dev.keynest.manager.vault;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** One in-flight bulk operation, including writing its encrypted response; no queued secrets. */
@Component
public class VaultExportAdmission {
  private final Semaphore slot = new Semaphore(1);
  public Lease acquire() {
    if (!slot.tryAcquire()) throw new VaultExportFailure(VaultExportFailure.Code.EXPORT_BUSY);
    return new Lease();
  }
  public final class Lease implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    @Override public void close() { if (closed.compareAndSet(false, true)) slot.release(); }
  }
}
