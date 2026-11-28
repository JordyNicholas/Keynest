package dev.keynest.manager.auth.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Consumer;

/** One committed worker claim. Secret copies are short-lived and never form a durable job payload. */
public final class VerificationDelivery implements AutoCloseable {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final UUID generation;
  private final UUID claim;
  private final VerificationRequest request;
  private final Instant expiresAt;
  private final Object tokenLock = new Object();
  private final char[] value;
  private final String digest;
  private volatile boolean closed;

  private VerificationDelivery(UUID generation, UUID claim, VerificationRequest request, Instant expiresAt,
    char[] value, String digest) {
    this.generation = generation;
    this.claim = claim;
    this.request = request;
    this.expiresAt = expiresAt;
    this.value = value.clone();
    this.digest = digest;
  }

  static VerificationDelivery mint(UUID generation, UUID claim, VerificationRequest request, Instant expiresAt) {
    byte[] entropy = new byte[32];
    byte[] encoded = null;
    char[] value = new char[43];
    try {
      RANDOM.nextBytes(entropy);
      encoded = Base64.getUrlEncoder().withoutPadding().encode(entropy);
      for (int i = 0; i < encoded.length; i++)
        value[i] = (char) encoded[i];
      return new VerificationDelivery(generation, claim, request, expiresAt, value, digest(encoded));
    } finally {
      Arrays.fill(entropy, (byte) 0);
      if (encoded != null)
        Arrays.fill(encoded, (byte) 0);
      Arrays.fill(value, '\0');
    }
  }

  static String digestPresented(char[] value) {
    if (value == null || value.length != 43)
      return null;
    byte[] encoded = new byte[43];
    try {
      for (int i = 0; i < value.length; i++) {
        char c = value[i];
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_'))
          return null;
        encoded[i] = (byte) c;
      }
      return digest(encoded);
    } finally {
      Arrays.fill(encoded, (byte) 0);
    }
  }

  static String digestClient(String identity) {
    return digest(identity.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  public UUID generation() {
    return generation;
  }
  public UUID claim() {
    return claim;
  }
  public VerificationRequest request() {
    return request;
  }
  public Instant expiresAt() {
    return expiresAt;
  }
  String digest() {
    return digest;
  }
  boolean closed() {
    return closed;
  }

  /** Does not hold the object's lock over caller I/O. Callers must not retain or log the copy. */
  public void useToken(Consumer<char[]> operation) {
    char[] copy;
    synchronized (tokenLock) {
      if (closed)
        throw new IllegalStateException("Verification token unavailable");
      copy = value.clone();
    }
    try {
      operation.accept(copy);
    } finally {
      Arrays.fill(copy, '\0');
    }
  }

  @Override
  public void close() {
    synchronized (tokenLock) {
      Arrays.fill(value, '\0');
      closed = true;
    }
  }
  @Override
  public String toString() {
    return "VerificationDelivery[redacted]";
  }
}
