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

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey secretKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            byte[] sealed = cipher.doFinal(plaintext);
            // Java returns ciphertext || tag
            int ctLen = sealed.length - 16;
            byte[] ciphertext = Arrays.copyOfRange(sealed, 0, ctLen);
            byte[] tag = Arrays.copyOfRange(sealed, ctLen, sealed.length);
            return new SealEnvelope(
                    SealEnvelope.VERSION_V1,
                    SealEnvelope.ALG_AES_256_GCM,
                    keyId.getBytes(StandardCharsets.UTF_8),
                    nonce,
                    ciphertext,
                    tag
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("seal failed", e);
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

        byte[] sealed = new byte[envelope.ciphertext().length + envelope.tag().length];
        System.arraycopy(envelope.ciphertext(), 0, sealed, 0, envelope.ciphertext().length);
        System.arraycopy(envelope.tag(), 0, sealed, envelope.ciphertext().length, envelope.tag().length);

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
        }
    }

    public String fingerprint(SealEnvelope envelope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(envelope.toBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(hash, 16));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("fingerprint failed", e);
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
