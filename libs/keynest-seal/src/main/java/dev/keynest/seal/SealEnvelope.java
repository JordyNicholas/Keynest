package dev.keynest.seal;

import java.util.Arrays;
import java.util.Objects;

/**
 * Keynest Seal envelope v1 — unique packaging over standard AES-256-GCM.
 *
 * <pre>
 * version (1) | algorithmId (1) | keyIdLen (2 BE) | keyId | nonce (12) | ciphertext | tag (16)
 * </pre>
 */
public final class SealEnvelope {

    public static final byte VERSION_V1 = 1;
    public static final byte ALG_AES_256_GCM = 1;

    private final byte version;
    private final byte algorithmId;
    private final byte[] keyId;
    private final byte[] nonce;
    private final byte[] ciphertext;
    private final byte[] tag;

    public SealEnvelope(byte version, byte algorithmId, byte[] keyId, byte[] nonce, byte[] ciphertext, byte[] tag) {
        this.version = version;
        this.algorithmId = algorithmId;
        this.keyId = Arrays.copyOf(keyId, keyId.length);
        this.nonce = Arrays.copyOf(nonce, nonce.length);
        this.ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        this.tag = Arrays.copyOf(tag, tag.length);
    }

    public byte version() {
        return version;
    }

    public byte algorithmId() {
        return algorithmId;
    }

    public byte[] keyId() {
        return Arrays.copyOf(keyId, keyId.length);
    }

    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    public byte[] tag() {
        return Arrays.copyOf(tag, tag.length);
    }

    public byte[] toBytes() {
        if (keyId.length > 0xFFFF) {
            throw new IllegalStateException("keyId too long");
        }
        byte[] out = new byte[1 + 1 + 2 + keyId.length + nonce.length + ciphertext.length + tag.length];
        int i = 0;
        out[i++] = version;
        out[i++] = algorithmId;
        out[i++] = (byte) ((keyId.length >> 8) & 0xFF);
        out[i++] = (byte) (keyId.length & 0xFF);
        System.arraycopy(keyId, 0, out, i, keyId.length);
        i += keyId.length;
        System.arraycopy(nonce, 0, out, i, nonce.length);
        i += nonce.length;
        System.arraycopy(ciphertext, 0, out, i, ciphertext.length);
        i += ciphertext.length;
        System.arraycopy(tag, 0, out, i, tag.length);
        return out;
    }

    public static SealEnvelope fromBytes(byte[] raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.length < 1 + 1 + 2 + 12 + 16) {
            throw new IllegalArgumentException("envelope too short");
        }
        int i = 0;
        byte version = raw[i++];
        byte algorithmId = raw[i++];
        int keyIdLen = ((raw[i++] & 0xFF) << 8) | (raw[i++] & 0xFF);
        if (i + keyIdLen + 12 + 16 > raw.length) {
            throw new IllegalArgumentException("invalid envelope lengths");
        }
        byte[] keyId = Arrays.copyOfRange(raw, i, i + keyIdLen);
        i += keyIdLen;
        byte[] nonce = Arrays.copyOfRange(raw, i, i + 12);
        i += 12;
        int ctLen = raw.length - i - 16;
        if (ctLen < 0) {
            throw new IllegalArgumentException("missing ciphertext/tag");
        }
        byte[] ciphertext = Arrays.copyOfRange(raw, i, i + ctLen);
        i += ctLen;
        byte[] tag = Arrays.copyOfRange(raw, i, i + 16);
        try {
            return new SealEnvelope(version, algorithmId, keyId, nonce, ciphertext, tag);
        } finally {
            // Constructor copies fields; wipe parse temporaries.
            Arrays.fill(keyId, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(tag, (byte) 0);
        }
    }
}
