package dev.keynest.manager.vault;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Canonical AAD encoding for binding an envelope to its owner, item and policy. */
final class VaultItemAad {

  static final int VERSION_ITEM_V1 = 1;
  private static final byte[] DOMAIN = "keynest:vault-item".getBytes(StandardCharsets.US_ASCII);

  private VaultItemAad() {
  }

  static byte[] itemV1(UUID userId, UUID itemId, String policyId) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(itemId, "itemId");
    Objects.requireNonNull(policyId, "policyId");
    byte[] policy = policyId.getBytes(StandardCharsets.UTF_8);

    return ByteBuffer.allocate(DOMAIN.length + 1 + 16 + 16 + Integer.BYTES + policy.length)
            .put(DOMAIN)
            .put((byte) VERSION_ITEM_V1)
            .putLong(userId.getMostSignificantBits())
            .putLong(userId.getLeastSignificantBits())
            .putLong(itemId.getMostSignificantBits())
            .putLong(itemId.getLeastSignificantBits())
            .putInt(policy.length)
            .put(policy)
            .array();
  }
}
