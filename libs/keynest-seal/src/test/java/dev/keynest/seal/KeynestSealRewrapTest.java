package dev.keynest.seal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeynestSealRewrapTest {

    private static final byte[] AAD = "policy:keynest.default.v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLAINTEXT = "correct-horse-battery".getBytes(StandardCharsets.UTF_8);

    @Test
    void rewrapRoundTripUnderNewKey() {
        KeynestSeal seal = new KeynestSeal();
        byte[] oldKey = seal.generateKey();
        byte[] newKey = seal.generateKey();

        SealEnvelope original = seal.seal(PLAINTEXT, oldKey, "kek-v1", AAD);
        SealEnvelope rewrapped = seal.rewrap(original, oldKey, newKey, "kek-v2", AAD);

        assertArrayEquals(PLAINTEXT, seal.open(rewrapped, newKey, AAD));
        assertArrayEquals("kek-v2".getBytes(StandardCharsets.UTF_8), rewrapped.keyId());
        assertFalse(Arrays.equals(original.toBytes(), rewrapped.toBytes()));
        assertNotEquals(seal.fingerprint(original), seal.fingerprint(rewrapped));
    }

    @Test
    void rewrapWithoutAad() {
        KeynestSeal seal = new KeynestSeal();
        byte[] oldKey = seal.generateKey();
        byte[] newKey = seal.generateKey();

        SealEnvelope original = seal.seal(PLAINTEXT, oldKey, "kid-old");
        SealEnvelope rewrapped = seal.rewrap(original, oldKey, newKey, "kid-new");

        assertArrayEquals(PLAINTEXT, seal.open(rewrapped, newKey));
        assertThrows(IllegalArgumentException.class, () -> seal.open(rewrapped, oldKey));
    }

    @Test
    void rewrapFailsWithWrongOldKey() {
        KeynestSeal seal = new KeynestSeal();
        byte[] oldKey = seal.generateKey();
        byte[] wrongKey = seal.generateKey();
        byte[] newKey = seal.generateKey();

        SealEnvelope original = seal.seal(PLAINTEXT, oldKey, "kek-v1", AAD);
        assertThrows(
                IllegalArgumentException.class,
                () -> seal.rewrap(original, wrongKey, newKey, "kek-v2", AAD));
    }

    @Test
    void rewrapFailsWithWrongAad() {
        KeynestSeal seal = new KeynestSeal();
        byte[] oldKey = seal.generateKey();
        byte[] newKey = seal.generateKey();
        byte[] otherAad = "policy:other".getBytes(StandardCharsets.UTF_8);

        SealEnvelope original = seal.seal(PLAINTEXT, oldKey, "kek-v1", AAD);
        assertThrows(
                IllegalArgumentException.class,
                () -> seal.rewrap(original, oldKey, newKey, "kek-v2", otherAad));
    }

    @Test
    void rewrappedEnvelopeRejectsOldKeyAndWrongAad() {
        KeynestSeal seal = new KeynestSeal();
        byte[] oldKey = seal.generateKey();
        byte[] newKey = seal.generateKey();

        SealEnvelope original = seal.seal(PLAINTEXT, oldKey, "kek-v1", AAD);
        SealEnvelope rewrapped = seal.rewrap(original, oldKey, newKey, "kek-v2", AAD);

        assertThrows(IllegalArgumentException.class, () -> seal.open(rewrapped, oldKey, AAD));
        assertThrows(
                IllegalArgumentException.class,
                () -> seal.open(rewrapped, newKey, "policy:other".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rewrapRoundTripThroughSerialization() {
        KeynestSeal seal = new KeynestSeal();
        byte[] oldKey = seal.generateKey();
        byte[] newKey = seal.generateKey();

        SealEnvelope original = seal.seal(PLAINTEXT, oldKey, "kek-v1", AAD);
        SealEnvelope rewrapped = seal.rewrap(original, oldKey, newKey, "kek-v2", AAD);
        SealEnvelope parsed = SealEnvelope.fromBytes(rewrapped.toBytes());

        assertArrayEquals(PLAINTEXT, seal.open(parsed, newKey, AAD));
    }

    @Test
    void rewrapRejectsNullNewKeyId() {
        KeynestSeal seal = new KeynestSeal();
        byte[] oldKey = seal.generateKey();
        byte[] newKey = seal.generateKey();
        SealEnvelope original = seal.seal(PLAINTEXT, oldKey, "kek-v1");

        assertThrows(NullPointerException.class, () -> seal.rewrap(original, oldKey, newKey, null));
    }
}
