package dev.keynest.manager.vault.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.seal.KeynestSeal;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedVaultBackupTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void readsIndependentPythonOpenSslVectorAndWipesPassphrase() throws Exception {
    JsonNode vector = vector();
    char[] passphrase = phrase(vector);
    assertEquals(vector.get("payload"), new EncryptedVaultBackup().decrypt(file(vector), passphrase));
    wiped(passphrase);
  }

  @Test
  void deterministicJavaWriterMatchesIndependentContainerBytes() throws Exception {
    JsonNode vector = vector();
    byte[] salt = HexFormat.of().parseHex(vector.get("saltHex").textValue());
    byte[] nonce = HexFormat.of().parseHex(vector.get("nonceHex").textValue());
    var codec = new EncryptedVaultBackup(new FixedRandom(salt), new KeynestSeal(new FixedRandom(nonce)),
      EncryptedVaultBackup::deriveKey);
    char[] passphrase = phrase(vector);
    assertArrayEquals(file(vector), codec.encrypt(vector.get("payload"), passphrase));
    wiped(passphrase);
  }

  @Test
  void differentCodecInstancesCanReadFilesWithoutSourceServerKeys() throws Exception {
    JsonNode vector = vector();
    ObjectNode payload = (ObjectNode) vector.get("payload").deepCopy();
    ((ObjectNode) payload.path("items").get(0)).put("password", "synthetic,\"雪🔑\"\r\n=sample");
    byte[] one = new EncryptedVaultBackup().encrypt(payload, phrase(vector));
    byte[] two = new EncryptedVaultBackup().encrypt(payload, phrase(vector));
    assertFalse(Arrays.equals(one, two));
    assertFalse(Arrays.equals(Arrays.copyOfRange(one, 10, 26), Arrays.copyOfRange(two, 10, 26)));
    assertFalse(Arrays.equals(Arrays.copyOfRange(one, 51, 63), Arrays.copyOfRange(two, 51, 63)));
    assertEquals(payload, new EncryptedVaultBackup().decrypt(one, phrase(vector)));
    assertEquals(payload, new EncryptedVaultBackup().decrypt(two, phrase(vector)));
    assertFalse(new String(one, StandardCharsets.ISO_8859_1).contains("synthetic"));
    // This tests file portability, not a database restore or third-party import.
  }

  @Test
  void wrongPassphraseAndAuthenticatedByteTamperingFailWithoutSensitiveCauses() throws Exception {
    JsonNode vector = vector();
    char[] wrong = "another-synthetic-passphrase".toCharArray();
    rejected(new EncryptedVaultBackup(), file(vector), wrong);
    for (int offset : new int[]{10, 25, 51, 62, 63, file(vector).length - 1}) {
      byte[] changed = file(vector);
      changed[offset] ^= 1;
      rejected(new EncryptedVaultBackup(), changed, phrase(vector));
    }
  }

  @Test
  void malformedHeadersAndUnsupportedProfilesAreRejectedBeforeKdf() throws Exception {
    JsonNode vector = vector();
    AtomicInteger calls = new AtomicInteger();
    var codec = observing(calls);
    for (int offset : new int[]{0, 7, 8, 9, 26, 29, 30, 31, 32, 33, 34, 50}) {
      byte[] changed = file(vector);
      changed[offset] ^= 1;
      rejected(codec, changed, phrase(vector));
    }
    byte[] excessive = file(vector);
    ByteBuffer.wrap(excessive, 26, 4).putInt(Integer.MAX_VALUE);
    rejected(codec, excessive, phrase(vector));
    rejected(codec, new byte[EncryptedVaultBackup.MAX_FILE_BYTES + 1], phrase(vector));
    rejected(codec, null, phrase(vector));
    rejected(codec, Arrays.copyOf(file(vector), file(vector).length + 1), phrase(vector));
    for (int size = 0; size < 80; size++) rejected(codec, Arrays.copyOf(file(vector), size), phrase(vector));
    assertEquals(0, calls.get());
  }

  @Test
  void truncatedCiphertextAndAppendedBytesCannotBypassLengthAndAuthentication() throws Exception {
    JsonNode vector = vector();
    byte[] truncated = Arrays.copyOf(file(vector), file(vector).length - 1);
    ByteBuffer.wrap(truncated, 26, 4).putInt(truncated.length - 30);
    rejected(new EncryptedVaultBackup(), truncated, phrase(vector));
    byte[] appended = Arrays.copyOf(file(vector), file(vector).length + 1);
    ByteBuffer.wrap(appended, 26, 4).putInt(appended.length - 30);
    rejected(new EncryptedVaultBackup(), appended, phrase(vector));
  }

  @Test
  void invalidPassphrasesAreConsumedWithoutStartingKdf() throws Exception {
    JsonNode vector = vector();
    AtomicInteger calls = new AtomicInteger();
    var codec = observing(calls);
    for (char[] phrase : new char[][]{null, new char[0], "short".toCharArray(), " ".repeat(15).toCharArray(),
      "x".repeat(1025).toCharArray(), ("x".repeat(14) + '\uD800').toCharArray(),
      ("x".repeat(14) + '\uDC00').toCharArray()}) {
      char[] encryptionPhrase = phrase == null ? null : phrase.clone();
      rejected(codec, file(vector), phrase);
      IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> codec.encrypt(vector.get("payload"), encryptionPhrase));
      assertNull(failure.getCause());
      if (encryptionPhrase != null) wiped(encryptionPhrase);
    }
    assertEquals(0, calls.get());
  }

  @Test
  void passphraseEncodingDoesNotTrimNormalizeOrChangeSupplementaryCharacters() throws Exception {
    JsonNode payload = vector().get("payload");
    String original = "  synthetic-é-🔑-phrase  ";
    byte[] file = new EncryptedVaultBackup().encrypt(payload, original.toCharArray());
    assertEquals(payload, new EncryptedVaultBackup().decrypt(file, original.toCharArray()));
    rejected(new EncryptedVaultBackup(), file, original.strip().toCharArray());
    rejected(new EncryptedVaultBackup(), file, original.replace("é", "e\u0301").toCharArray());
  }

  @Test
  void invalidPayloadIsRejectedBeforeKeyDerivationAndPassphraseIsWiped() throws Exception {
    JsonNode vector = vector();
    AtomicInteger calls = new AtomicInteger();
    char[] phrase = phrase(vector);
    assertThrows(IllegalArgumentException.class, () -> observing(calls).encrypt(JSON.createObjectNode(), phrase));
    assertEquals(0, calls.get());
    wiped(phrase);
  }

  @Test
  void derivedKeysAreClearedOnSuccessAndAfterAuthenticatedPayloadRejection() throws Exception {
    JsonNode vector = vector();
    AtomicReference<byte[]> issued = new AtomicReference<>();
    var codec = new EncryptedVaultBackup(new SecureRandom(), new KeynestSeal(), (phrase, salt) -> {
      byte[] key = new byte[32];
      Arrays.fill(key, (byte) 42);
      issued.set(key);
      return key;
    });
    byte[] file = codec.encrypt(vector.get("payload"), phrase(vector));
    assertArrayEquals(new byte[32], issued.get());
    assertEquals(vector.get("payload"), codec.decrypt(file, phrase(vector)));
    assertArrayEquals(new byte[32], issued.get());
    // Correct GCM tag, invalid JSON: authentication must not make malformed content acceptable.
    byte[] malformed = authenticatedBytes("sensitive-parser-sentinel".getBytes(StandardCharsets.UTF_8));
    rejected(codec, malformed, phrase(vector));
    assertArrayEquals(new byte[32], issued.get());
    ObjectNode unsupported = (ObjectNode) vector.get("payload").deepCopy();
    unsupported.put("version", 2);
    rejected(codec, authenticatedBytes(JSON.writeValueAsBytes(unsupported)), phrase(vector));
    assertArrayEquals(new byte[32], issued.get());
  }

  @Test
  void providerFailureIsSanitizedAndDoesNotLeakTheConcurrencySlot() throws Exception {
    JsonNode vector = vector();
    char[] phrase = phrase(vector);
    var broken = new EncryptedVaultBackup(new SecureRandom(), new KeynestSeal(), (p, s) -> {
      throw new IllegalStateException("sensitive-provider-sentinel");
    });
    rejected(broken, file(vector), phrase);
    assertDoesNotThrow(() -> observing(new AtomicInteger()).encrypt(vector.get("payload"), phrase(vector)));
    AtomicReference<byte[]> wrongSize = new AtomicReference<>(new byte[16]);
    Arrays.fill(wrongSize.get(), (byte) 42);
    var wrongKey = new EncryptedVaultBackup(new SecureRandom(), new KeynestSeal(), (p, s) -> wrongSize.get());
    assertThrows(IllegalArgumentException.class, () -> wrongKey.encrypt(vector.get("payload"), phrase(vector)));
    assertArrayEquals(new byte[16], wrongSize.get());
  }

  @Test
  void onlyOneBackupWorkSlotExistsAcrossCodecInstancesAndRejectedPassphraseIsWiped() throws Exception {
    JsonNode vector = vector();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger secondCalls = new AtomicInteger();
    var blocked = new EncryptedVaultBackup(new SecureRandom(), new KeynestSeal(), (p, s) -> {
      entered.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException();
      }
      return new byte[32];
    });
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future = executor.submit(() -> blocked.encrypt(vector.get("payload"), phrase(vector)));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      char[] phrase = phrase(vector);
      assertThrows(EncryptedVaultBackup.Busy.class, () -> observing(secondCalls).decrypt(file(vector), phrase));
      wiped(phrase);
      assertEquals(0, secondCalls.get());
      release.countDown();
      assertNotNull(future.get(5, TimeUnit.SECONDS));
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
    assertDoesNotThrow(() -> observing(secondCalls).encrypt(vector.get("payload"), phrase(vector)));
  }

  private static byte[] authenticatedBytes(byte[] plaintext) {
    byte[] key = new byte[32];
    Arrays.fill(key, (byte) 42);
    int overhead = 4 + EncryptedVaultBackup.KEY_ID.length() + 12 + 16;
    byte[] header = ByteBuffer.allocate(30).put("KEYNESTB".getBytes(StandardCharsets.US_ASCII))
      .put((byte) 1).put((byte) 1).put(new byte[16]).putInt(plaintext.length + overhead).array();
    byte[] envelope = new KeynestSeal().seal(plaintext, key, EncryptedVaultBackup.KEY_ID, header).toBytes();
    Arrays.fill(key, (byte) 0);
    return ByteBuffer.allocate(header.length + envelope.length).put(header).put(envelope).array();
  }

  private static EncryptedVaultBackup observing(AtomicInteger calls) {
    return new EncryptedVaultBackup(new SecureRandom(), new KeynestSeal(), (phrase, salt) -> {
      calls.incrementAndGet();
      return new byte[32];
    });
  }

  private static void rejected(EncryptedVaultBackup codec, byte[] file, char[] phrase) {
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> codec.decrypt(file, phrase));
    assertEquals("Unable to open or create this Keynest backup", failure.getMessage());
    assertNull(failure.getCause());
    if (phrase != null) wiped(phrase);
  }

  private static void wiped(char[] phrase) {
    assertArrayEquals(new char[phrase.length], phrase);
  }

  private static JsonNode vector() throws Exception {
    try (var source = EncryptedVaultBackupTest.class.getResourceAsStream("/vectors/portable-backup-v1.json")) {
      assertNotNull(source);
      return JSON.readTree(source);
    }
  }

  private static char[] phrase(JsonNode vector) {
    return vector.get("passphrase").textValue().toCharArray();
  }

  private static byte[] file(JsonNode vector) {
    return Base64.getDecoder().decode(vector.get("containerBase64").textValue());
  }

  private static final class FixedRandom extends SecureRandom {
    private final byte[] value;
    FixedRandom(byte[] value) { this.value = value.clone(); }
    @Override public void nextBytes(byte[] bytes) {
      assertEquals(value.length, bytes.length);
      System.arraycopy(value, 0, bytes, 0, bytes.length);
    }
  }
}
