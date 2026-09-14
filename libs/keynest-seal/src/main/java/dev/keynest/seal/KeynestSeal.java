package dev.keynest.seal;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Keynest Seal toolkit: seal/open envelopes and Argon2id password hash/verify.
 *
 * <p>Intermediate {@code byte[]} buffers created inside seal/open/rewrap/fingerprint are
 * zeroed when practical. Caller-owned inputs (keys, plaintext) and returned plaintext are
 * not cleared — see ADR-0003 buffer-hygiene note for JVM limits.
 */
public final class KeynestSeal {

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_LEN = 12;
    private static final int KEY_LEN = 32;

    private final SecureRandom random;

    public KeynestSeal() {
        this(new SecureRandom());
    }

    public KeynestSeal(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public SealEnvelope seal(byte[] plaintext, byte[] key, String keyId) {
        return seal(plaintext, key, keyId, null);
    }

    public SealEnvelope seal(byte[] plaintext, byte[] key, String keyId, byte[] aad) {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(keyId, "keyId");
        requireKeyLength(key);

        byte[] nonce = new byte[NONCE_LEN];
        random.nextBytes(nonce);
        byte[] keyIdBytes = keyId.getBytes(StandardCharsets.UTF_8);
        byte[] sealed = null;
        byte[] ciphertext = null;
        byte[] tag = null;

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey secretKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            sealed = cipher.doFinal(plaintext);
            // Java returns ciphertext || tag
            int ctLen = sealed.length - 16;
            ciphertext = Arrays.copyOfRange(sealed, 0, ctLen);
            tag = Arrays.copyOfRange(sealed, ctLen, sealed.length);
            return new SealEnvelope(
                    SealEnvelope.VERSION_V1,
                    SealEnvelope.ALG_AES_256_GCM,
                    keyIdBytes,
                    nonce,
                    ciphertext,
                    tag
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("seal failed", e);
        } finally {
            // Envelope stores defensive copies; local intermediates can be wiped.
            SensitiveBuffers.clear(sealed, ciphertext, tag, nonce, keyIdBytes);
        }
    }

    public byte[] open(SealEnvelope envelope, byte[] key) {
        return open(envelope, key, null);
    }

    public byte[] open(SealEnvelope envelope, byte[] key, byte[] aad) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(key, "key");
        requireKeyLength(key);

        if (envelope.version() != SealEnvelope.VERSION_V1) {
            throw new IllegalArgumentException("unsupported envelope version: " + envelope.version());
        }
        if (envelope.algorithmId() != SealEnvelope.ALG_AES_256_GCM) {
            throw new IllegalArgumentException("unsupported algorithm: " + envelope.algorithmId());
        }

        byte[] ciphertext = envelope.ciphertext();
        byte[] tag = envelope.tag();
        byte[] sealed = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, sealed, 0, ciphertext.length);
        System.arraycopy(tag, 0, sealed, ciphertext.length, tag.length);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey secretKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, envelope.nonce()));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(sealed);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("open failed: integrity check or key mismatch", e);
        } finally {
            SensitiveBuffers.clear(ciphertext, tag, sealed);
        }
    }

    /**
     * Re-encrypts an envelope under a new key without changing the plaintext.
     * Uses the same AAD binding on open and seal; produces a new nonce and {@code newKeyId}.
     */
    public SealEnvelope rewrap(SealEnvelope envelope, byte[] oldKey, byte[] newKey, String newKeyId) {
        return rewrap(envelope, oldKey, newKey, newKeyId, null);
    }

    public SealEnvelope rewrap(
            SealEnvelope envelope, byte[] oldKey, byte[] newKey, String newKeyId, byte[] aad) {
        Objects.requireNonNull(newKeyId, "newKeyId");
        byte[] plaintext = open(envelope, oldKey, aad);
        try {
            return seal(plaintext, newKey, newKeyId, aad);
        } finally {
            SensitiveBuffers.clear(plaintext);
        }
    }

    public String fingerprint(SealEnvelope envelope) {
        byte[] raw = null;
        byte[] hash = null;
        byte[] truncated = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            raw = envelope.toBytes();
            hash = digest.digest(raw);
            truncated = Arrays.copyOf(hash, 16);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("fingerprint failed", e);
        } finally {
            SensitiveBuffers.clear(raw, hash, truncated);
        }
    }

    public byte[] generateKey() {
        byte[] key = new byte[KEY_LEN];
        random.nextBytes(key);
        return key;
    }

    private static void requireKeyLength(byte[] key) {
        if (key.length != KEY_LEN) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
    }
}
