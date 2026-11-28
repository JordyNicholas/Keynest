package dev.keynest.manager.auth.verification;

import java.util.Locale;
import java.util.UUID;

/** Internal, already-parsed identity input. These bounds are not a mailbox syntax parser. */
public record VerificationRequest(Purpose purpose, String recipient, String deliveryAddress, OwnerBinding owner) {
  public VerificationRequest {
    if (purpose == null || recipient == null || deliveryAddress == null || recipient.isBlank()
      || recipient.length() > 320 || deliveryAddress.length() > 320 || !deliveryAddress.equals(deliveryAddress.trim())
      || deliveryAddress.chars().anyMatch(c -> c < 32 || c > 126)
      || !recipient.equals(deliveryAddress.toLowerCase(Locale.ROOT))
      || (purpose == Purpose.NEW_ACCOUNT) != (owner == null)) {
      throw new IllegalArgumentException("Invalid verification request");
    }
  }

  @Override
  public String toString() {
    return "VerificationRequest[redacted]";
  }

  public enum Purpose {
    NEW_ACCOUNT, EXISTING_OWNER
  }

  public record OwnerBinding(UUID ownerId, String sessionDigest) {
    public OwnerBinding {
      if (ownerId == null || sessionDigest == null || !sessionDigest.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("Invalid verification owner binding");
      }
    }
    @Override
    public String toString() {
      return "VerificationOwner[redacted]";
    }
  }
}
