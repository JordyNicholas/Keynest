package dev.keynest.manager.vault.export;

import com.fasterxml.jackson.databind.JsonNode;
import de.mkammerer.argon2.Argon2Factory;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.SealEnvelope;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Passphrase-protected portable files. This is not an application restore operation.
 * No server key, persistence, HTTP route or stored Vault envelope is involved.
 * Both operations consume and wipe the supplied passphrase, including on rejection.
 */
public final class EncryptedVaultBackup {
  private static final byte[] MAGIC = "KEYNESTB".getBytes(StandardCharsets.US_ASCII);
  private static final byte CONTAINER_VERSION = 1;
  private static final byte KDF_PROFILE = 1;
  private static final int SALT_BYTES = 16;
  private static final int SALT_OFFSET = 10;
  private static final int LENGTH_OFFSET = 26;
  static final int HEADER_BYTES = 30;
  static final String KEY_ID = "keynest.backup.v1";
  private static final byte[] KEY_ID_BYTES = KEY_ID.getBytes(StandardCharsets.US_ASCII);
  private static final int ENVELOPE_OVERHEAD = 4 + KEY_ID_BYTES.length + 12 + 16;
  public static final int MAX_FILE_BYTES = HEADER_BYTES + ENVELOPE_OVERHEAD + PortableVaultFormat.MAX_BYTES;
  // Separate from authentication's configured password-work budget. Shared across codec instances.
  private static final Semaphore SLOT = new Semaphore(1);

  private final SecureRandom random;
  private final KeynestSeal seal;
  private final KeyDerivation keys;

  public EncryptedVaultBackup() {
    this(new SecureRandom(), new KeynestSeal(), EncryptedVaultBackup::deriveKey);
  }

  // Only package-local tests substitute deterministic entropy or observe KDF calls.
  EncryptedVaultBackup(SecureRandom random, KeynestSeal seal, KeyDerivation keys) {
    this.random = Objects.requireNonNull(random);
    this.seal = Objects.requireNonNull(seal);
    this.keys = Objects.requireNonNull(keys);
  }

  public byte[] encrypt(JsonNode document, char[] passphrase) {
    byte[] plaintext = null;
    byte[] key = null;
    byte[] salt = new byte[SALT_BYTES];
    byte[] header = null;
    byte[] envelope = null;
    boolean acquired = false;
    try {
      requirePassphrase(passphrase);
      acquired = SLOT.tryAcquire();
      if (!acquired) throw new Busy();
      plaintext = PortableVaultFormat.encode(document);
      random.nextBytes(salt);
      header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).put(CONTAINER_VERSION).put(KDF_PROFILE)
        .put(salt).putInt(plaintext.length + ENVELOPE_OVERHEAD).array();
      key = keys.derive(passphrase, salt);
      requireKey(key);
      envelope = seal.seal(plaintext, key, KEY_ID, header).toBytes();
      return ByteBuffer.allocate(header.length + envelope.length).put(header).put(envelope).array();
    } catch (Busy busy) {
      throw busy;
    } catch (Exception ignored) {
      throw invalid();
    } finally {
      clear(plaintext, key, salt, header, envelope);
      if (passphrase != null) Arrays.fill(passphrase, '\0');
      if (acquired) SLOT.release();
    }
  }

  public JsonNode decrypt(byte[] file, char[] passphrase) {
    byte[] header = null;
    byte[] envelope = null;
    byte[] salt = null;
    byte[] key = null;
    byte[] plaintext = null;
    boolean acquired = false;
    try {
      requirePassphrase(passphrase);
      // Size, fixed profile, exact framing and static Seal fields are checked before KDF/allocation.
      checkFraming(file);
      acquired = SLOT.tryAcquire();
      if (!acquired) throw new Busy();
      header = Arrays.copyOf(file, HEADER_BYTES);
      salt = Arrays.copyOfRange(file, SALT_OFFSET, LENGTH_OFFSET);
      envelope = Arrays.copyOfRange(file, HEADER_BYTES, file.length);
      key = keys.derive(passphrase, salt);
      requireKey(key);
      plaintext = seal.open(SealEnvelope.fromBytes(envelope), key, header);
      // Parse/schema validation occurs only after GCM authenticates the complete payload.
      return PortableVaultFormat.decode(plaintext);
    } catch (Busy busy) {
      throw busy;
    } catch (Exception ignored) {
      // No parser/provider messages, causes, passphrases or decrypted input escape this boundary.
      throw invalid();
    } finally {
      clear(header, envelope, salt, key, plaintext);
      if (passphrase != null) Arrays.fill(passphrase, '\0');
      if (acquired) SLOT.release();
    }
  }

  private static void checkFraming(byte[] file) {
    if (file == null || file.length <= HEADER_BYTES + ENVELOPE_OVERHEAD || file.length > MAX_FILE_BYTES) {
      throw invalid();
    }
    for (int i = 0; i < MAGIC.length; i++) if (file[i] != MAGIC[i]) throw invalid();
    if (file[8] != CONTAINER_VERSION || file[9] != KDF_PROFILE
      || ByteBuffer.wrap(file, LENGTH_OFFSET, 4).getInt() != file.length - HEADER_BYTES) throw invalid();
    int offset = HEADER_BYTES;
    if (file[offset] != SealEnvelope.VERSION_V1 || file[offset + 1] != SealEnvelope.ALG_AES_256_GCM
      || file[offset + 2] != 0 || Byte.toUnsignedInt(file[offset + 3]) != KEY_ID_BYTES.length) throw invalid();
    for (int i = 0; i < KEY_ID_BYTES.length; i++) {
      if (file[offset + 4 + i] != KEY_ID_BYTES[i]) throw invalid();
    }
  }

  private static void requirePassphrase(char[] passphrase) {
    // UTF-8 with no trimming or Unicode normalization. Reject malformed surrogate sequences.
    if (passphrase == null || passphrase.length > 1024
      || Character.codePointCount(passphrase, 0, passphrase.length) < 15) throw invalid();
    boolean visible = false;
    for (int i = 0; i < passphrase.length; i++) {
      char value = passphrase[i];
      if (!Character.isWhitespace(value)) visible = true;
      if (Character.isHighSurrogate(value)) {
        if (++i >= passphrase.length || !Character.isLowSurrogate(passphrase[i])) throw invalid();
      } else if (Character.isLowSurrogate(value)) throw invalid();
    }
    if (!visible) throw invalid();
  }

  private static void requireKey(byte[] key) {
    if (key == null || key.length != 32) throw invalid();
  }

  static byte[] deriveKey(char[] passphrase, byte[] salt) {
    // RFC 9106's second recommended profile: Argon2id v1.3, 64 MiB, t=3, p=4, 256-bit key.
    return Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id, SALT_BYTES, 32)
      .rawHash(3, 65536, 4, passphrase, StandardCharsets.UTF_8, salt);
  }

  private static void clear(byte[]... buffers) {
    for (byte[] buffer : buffers) if (buffer != null) Arrays.fill(buffer, (byte) 0);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Unable to open or create this Keynest backup");
  }

  static final class Busy extends IllegalStateException {
    Busy() { super("A backup operation is already running. Try again shortly."); }
  }

  @FunctionalInterface
  interface KeyDerivation {
    byte[] derive(char[] passphrase, byte[] salt);
  }
}
