package dev.keynest.manager.vault;

import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.SealEnvelope;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultItemAadTest {

  private static final UUID USER = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID ITEM = UUID.fromString("60000000-0000-0000-0000-000000000001");
  private static final String POLICY = "keynest.default.v1";

  @Test
  void encodingIsDeterministicAndChangesWithEveryBindingField() {
    byte[] aad = VaultItemAad.itemV1(USER, ITEM, POLICY);

    assertArrayEquals(aad, VaultItemAad.itemV1(USER, ITEM, POLICY));
    assertFalse(java.util.Arrays.equals(aad, VaultItemAad.itemV1(
            UUID.fromString("50000000-0000-0000-0000-000000000002"), ITEM, POLICY
    )));
    assertFalse(java.util.Arrays.equals(aad, VaultItemAad.itemV1(
            USER, UUID.fromString("60000000-0000-0000-0000-000000000002"), POLICY
    )));
    assertFalse(java.util.Arrays.equals(aad, VaultItemAad.itemV1(USER, ITEM, "other-policy")));
  }

  @Test
  void envelopeCannotBeOpenedUnderAnotherItemBinding() {
    KeynestSeal seal = new KeynestSeal();
    byte[] key = seal.generateKey();
    byte[] plaintext = "correct horse battery staple".getBytes(StandardCharsets.UTF_8);
    byte[] aad = VaultItemAad.itemV1(USER, ITEM, POLICY);
    SealEnvelope envelope = seal.seal(plaintext, key, "test-key", aad);

    assertArrayEquals(plaintext, seal.open(envelope, key, aad));
    byte[] otherItemAad = VaultItemAad.itemV1(
            USER, UUID.fromString("60000000-0000-0000-0000-000000000002"), POLICY
    );
    assertThrows(IllegalArgumentException.class, () -> seal.open(envelope, key, otherItemAad));
  }
}
