package dev.keynest.manager.auth;

import dev.keynest.manager.config.LoginThrottleProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Bounded process-local admission, independent of account existence and client IP. */
@Component
public class LoginAttemptLimiter {
  private final Map<String, Entry> entries = new HashMap<>();
  private final SecretKey identityKey;
  private final int failureLimit;
  private final int maxEntries;
  private final long windowNanos;
  private final long cooldownNanos;
  private final LongSupplier clock;
  private long lastPrune;
  private boolean pruned;

  @Autowired
  public LoginAttemptLimiter(LoginThrottleProperties properties) {
    this(properties, System::nanoTime);
  }

  LoginAttemptLimiter(LoginThrottleProperties properties, LongSupplier clock) {
    bounded(properties.getFailureLimit(), 100);
    bounded(properties.getWindowSeconds(), 3600);
    bounded(properties.getCooldownSeconds(), 3600);
    bounded(properties.getMaxEntries(), 100_000);
    failureLimit = properties.getFailureLimit();maxEntries = properties.getMaxEntries();
    windowNanos = TimeUnit.SECONDS.toNanos(properties.getWindowSeconds());
    cooldownNanos = TimeUnit.SECONDS.toNanos(properties.getCooldownSeconds());
    this.clock = clock;
    byte[] key = new byte[32];
    try { new SecureRandom().nextBytes(key);identityKey = new SecretKeySpec(key, "HmacSHA256"); }
    finally { Arrays.fill(key, (byte) 0); }
  }

  public Attempt begin(String normalizedIdentifier) {
    if (normalizedIdentifier == null || normalizedIdentifier.isBlank()
      || normalizedIdentifier.length() > UserAccount.EMAIL_MAX_LENGTH) {
      throw new IllegalArgumentException("Invalid login identifier");
    }
    String key = identity(normalizedIdentifier);
    synchronized (this) {
      long now = clock.getAsLong();
      Entry entry = entries.get(key);
      if (entry != null && expired(entry, now)) { entries.remove(key);entry = null; }
      if (entry != null && entry.active) throw limited(1);
      if (entry != null && entry.cooling) {
        throw limited(seconds(cooldownNanos - elapsed(now, entry.blockedAt)));
      }
      if (entry == null) {
        if (entries.size() >= maxEntries) prune(now);
        if (entries.size() >= maxEntries) throw limited(1);
        entry = new Entry();entries.put(key, entry);
      }
      entry.active = true;
      return new Attempt(key, entry);
    }
  }

  private boolean expired(Entry entry, long now) {
    if (entry.active) return false;
    return entry.cooling ? elapsed(now, entry.blockedAt) >= cooldownNanos
      : entry.failures == 0 || elapsed(now, entry.windowStarted) >= windowNanos;
  }

  private void prune(long now) {
    // A full table must not cause a complete scan on every rejected identifier.
    if (pruned && elapsed(now, lastPrune) < TimeUnit.SECONDS.toNanos(1)) return;
    lastPrune = now;pruned = true;
    entries.values().removeIf(entry -> expired(entry, now));
  }

  private String identity(String identifier) {
    byte[] bytes = identifier.getBytes(StandardCharsets.UTF_8);
    byte[] digest = null;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");mac.init(identityKey);
      digest = mac.doFinal(bytes);
      return HexFormat.of().formatHex(digest);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("Login admission unavailable");
    } finally {
      Arrays.fill(bytes, (byte) 0);
      if (digest != null) Arrays.fill(digest, (byte) 0);
    }
  }

  private static void bounded(int value, int maximum) {
    if (value < 1 || value > maximum) throw new IllegalArgumentException("Login throttle settings are out of bounds");
  }

  private static long elapsed(long now, long started) { return Math.max(0, now - started); }
  private static long seconds(long nanos) { return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L); }
  private static ResponseStatusException limited(long retrySeconds) {
    return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Sign-in is temporarily unavailable. Try again later.") {
      @Override public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retrySeconds));
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        return headers;
      }
    };
  }

  public final class Attempt implements AutoCloseable {
    private final String key;
    private final Entry entry;
    private boolean completed;

    private Attempt(String key, Entry entry) { this.key = key;this.entry = entry; }
    public void failed() { complete(Boolean.FALSE); }
    public void succeeded() { complete(Boolean.TRUE); }
    @Override public void close() { complete(null); }

    private void complete(Boolean valid) {
      synchronized (LoginAttemptLimiter.this) {
        if (completed) return;
        completed = true;
        if (entries.get(key) != entry) return;
        entry.active = false;
        if (Boolean.TRUE.equals(valid)) { entries.remove(key);return; }
        if (Boolean.FALSE.equals(valid)) {
          long now = clock.getAsLong();
          if (entry.failures == 0 || elapsed(now, entry.windowStarted) >= windowNanos) {
            entry.failures = 0;entry.windowStarted = now;
          }
          if (++entry.failures >= failureLimit) { entry.cooling = true;entry.blockedAt = now; }
        } else if (entry.failures == 0) entries.remove(key);
      }
    }
  }

  private static final class Entry {
    private int failures;
    private long windowStarted;
    private long blockedAt;
    private boolean active;
    private boolean cooling;
  }
}
