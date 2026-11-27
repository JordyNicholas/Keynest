package dev.keynest.manager.security;

import dev.keynest.seal.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Shares one password-work budget across the Manager API. */
@Service
public class PasswordHashingService {

  private final PasswordHasher hasher;
  private final Semaphore slots;

  public PasswordHashingService(
    PasswordHasher hasher,
    @Value("${keynest.password-hashing.max-concurrent}") int maxConcurrent
  ) {
    if (maxConcurrent < 1) {
      throw new IllegalArgumentException("keynest.password-hashing.max-concurrent must be positive");
    }
    this.hasher = hasher;
    this.slots = new Semaphore(maxConcurrent);
  }

  public String hash(String password) {
    return withSlot(() -> hasher.hashPassword(password.toCharArray()));
  }

  public boolean verify(String password, String hash) {
    return withSlot(() -> hasher.verifyPassword(password.toCharArray(), hash));
  }

  /** Consumes a caller-owned new password even when capacity is unavailable. */
  public String hashAndClear(char[] password) {
    try { return withSlot(() -> hasher.hashPassword(password)); }
    finally { if (password != null) java.util.Arrays.fill(password, '\0'); }
  }

  /** Consumes caller-owned credentials, including when the shared work budget is full. */
  public boolean verifyAndClear(char[] password, String hash) {
    try {
      return withSlot(() -> hasher.verifyPassword(password, hash));
    } finally {
      if (password != null) java.util.Arrays.fill(password, '\0');
    }
  }

  private <T> T withSlot(Supplier<T> operation) {
    if (!slots.tryAcquire()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "password operations are busy");
    }
    try {
      return operation.get();
    } finally {
      slots.release();
    }
  }
}
