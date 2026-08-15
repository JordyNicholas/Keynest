package dev.keynest.seal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeynestSealTest {

    @Test
    void sealOpenRoundTrip() {
        KeynestSeal seal = new KeynestSeal();
        byte[] key = seal.generateKey();
        byte[] plaintext = "hunter2-secret".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "policy:keynest.default.v1".getBytes(StandardCharsets.UTF_8);

        SealEnvelope envelope = seal.seal(plaintext, key, "kid-1", aad);
        byte[] opened = seal.open(envelope, key, aad);
        assertArrayEquals(plaintext, opened);

        SealEnvelope parsed = SealEnvelope.fromBytes(envelope.toBytes());
        assertArrayEquals(plaintext, seal.open(parsed, key, aad));
        assertNotEquals("", seal.fingerprint(envelope));
    }

    @Test
    void openFailsWithWrongKey() {
        KeynestSeal seal = new KeynestSeal();
        byte[] key = seal.generateKey();
        byte[] other = seal.generateKey();
        SealEnvelope envelope = seal.seal("x".getBytes(StandardCharsets.UTF_8), key, "kid");
        assertThrows(IllegalArgumentException.class, () -> seal.open(envelope, other));
    }

    @Test
    void passwordHashVerify() {
        PasswordHasher hasher = new PasswordHasher(2, 8192, 1);
        String hash = hasher.hashPassword("correct horse battery".toCharArray());
        assertTrue(hasher.verifyPassword("correct horse battery".toCharArray(), hash));
        assertFalse(hasher.verifyPassword("wrong".toCharArray(), hash));
    }
}
