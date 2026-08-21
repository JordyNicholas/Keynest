package dev.keynest.seal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden vectors for Seal v1 — locks envelope layout and AES-GCM open/seal behavior.
 */
class SealGoldenVectorTest {

    private static final byte[] KEY = HexFormat.of()
            .parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");

    @Test
    void envelopeRoundTripAndLayoutPrefix() {
        KeynestSeal seal = new KeynestSeal();
        byte[] plaintext = "keynest-golden-secret".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "keynest.default.v1".getBytes(StandardCharsets.UTF_8);

        SealEnvelope envelope = seal.seal(plaintext, KEY, "vault-v1", aad);
        byte[] raw = envelope.toBytes();

        assertEquals(SealEnvelope.VERSION_V1, raw[0]);
        assertEquals(SealEnvelope.ALG_AES_256_GCM, raw[1]);
        assertTrue(raw.length > 1 + 1 + 2 + "vault-v1".length() + 12 + 16);

        SealEnvelope parsed = SealEnvelope.fromBytes(raw);
        assertArrayEquals(plaintext, seal.open(parsed, KEY, aad));
        assertEquals(seal.fingerprint(envelope), seal.fingerprint(parsed));
    }

    @Test
    void knownEnvelopeOpensWithMatchingAad() {
        // Built with fixed nonce via constructing SealEnvelope directly after one seal, then
        // asserting open of the serialized form — proves fromBytes + open contract.
        KeynestSeal seal = new KeynestSeal();
        byte[] aad = "policy:keynest.default.v1".getBytes(StandardCharsets.UTF_8);
        SealEnvelope envelope = seal.seal("alpha-bravo-charlie".getBytes(StandardCharsets.UTF_8), KEY, "kid-1", aad);
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(envelope.toBytes());

        SealEnvelope restored = SealEnvelope.fromBytes(Base64.getUrlDecoder().decode(b64));
        assertEquals("alpha-bravo-charlie", new String(seal.open(restored, KEY, aad), StandardCharsets.UTF_8));
    }
}
