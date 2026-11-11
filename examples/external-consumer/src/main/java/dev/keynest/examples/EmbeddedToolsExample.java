package dev.keynest.examples;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PasswordValidator;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.SealEnvelope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.Supplier;

/** No network, Manager, Spring, configured keys or printed secret material. */
public final class EmbeddedToolsExample {

  private EmbeddedToolsExample() {
  }

  public static void main(String[] args) {
    PasswordPolicy policy = PasswordPolicy.defaultPolicy();
    String generated = new PasswordGenerator().generate(policy);
    require(PasswordValidator.validate(generated, policy).valid(), "generated value must satisfy the same policy");
    KeynestSeal seal = new KeynestSeal();
    byte[] firstKey = seal.generateKey();
    byte[] secondKey = seal.generateKey();
    byte[] plaintext = generated.getBytes(StandardCharsets.UTF_8);
    // The consumer owns this context; it is not Manager's item AAD format.
    byte[] aad = ("example:embedded:" + policy.id()).getBytes(StandardCharsets.UTF_8);
    byte[] opened = null;
    byte[] rotatedPlaintext = null;
    try {
      SealEnvelope stored = SealEnvelope.fromBytes(seal.seal(plaintext, firstKey, "example-key-1", aad).toBytes());
      opened = seal.open(stored, firstKey, aad);
      require(MessageDigest.isEqual(plaintext, opened), "stored envelope must preserve the value");
      SealEnvelope rotated = seal.rewrap(stored, firstKey, secondKey, "example-key-2", aad);
      rotatedPlaintext = seal.open(rotated, secondKey, aad);
      require(MessageDigest.isEqual(plaintext, rotatedPlaintext), "rewrap must preserve the value");
      requireRejected(() -> seal.open(rotated, firstKey, aad));
      requireRejected(() -> seal.open(stored, firstKey, "wrong-context".getBytes(StandardCharsets.UTF_8)));
    } finally {
      for (byte[] buffer : new byte[][]{firstKey, secondKey, plaintext, opened, rotatedPlaintext}) {
        if (buffer != null) Arrays.fill(buffer, (byte) 0);
      }
    }
    System.out.println("PASS: standalone generation, policy validation, seal/open, rewrap and context rejection");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static void requireRejected(Supplier<byte[]> operation) {
    try {
      byte[] unexpected = operation.get();
      Arrays.fill(unexpected, (byte) 0);
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new IllegalStateException("mismatched key/context must be rejected");
  }
}
