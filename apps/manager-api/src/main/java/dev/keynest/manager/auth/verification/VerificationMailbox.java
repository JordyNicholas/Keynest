package dev.keynest.manager.auth.verification;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;

/** Supported new-mail syntax only. Existing login identity acceptance is unchanged. */
public record VerificationMailbox(String identity, String delivery) {
  public VerificationMailbox {
    if (identity == null || delivery == null || !identity.equals(delivery.toLowerCase(Locale.ROOT)))
      throw new IllegalArgumentException("Invalid verification mailbox");
    validate(delivery);
  }

  public static VerificationMailbox parse(String input) {
    if (input == null || input.length() > 320 || input.chars().anyMatch(c -> c < 32 || c > 126))
      throw new IllegalArgumentException("Invalid verification mailbox");
    String delivery = input.trim();
    return new VerificationMailbox(delivery.toLowerCase(Locale.ROOT), delivery);
  }

  private static void validate(String delivery) {
    if (delivery.isEmpty() || delivery.length() > 254 || delivery.chars().anyMatch(c -> c < 32 || c > 126))
      throw new IllegalArgumentException("Invalid verification mailbox");
    try {
      InternetAddress[] addresses = InternetAddress.parse(delivery, true);
      if (addresses.length != 1 || addresses[0].isGroup() || addresses[0].getPersonal() != null
        || !delivery.equals(addresses[0].getAddress()))
        throw new IllegalArgumentException("Invalid verification mailbox");
      addresses[0].validate();
      int at = delivery.lastIndexOf('@');
      if (at < 1 || at > 64)
        throw new IllegalArgumentException("Invalid verification mailbox");
      String domain = delivery.substring(at + 1);
      if (domain.isEmpty() || domain.length() > 253)
        throw new IllegalArgumentException("Invalid verification mailbox");
      for (String label : domain.split("\\.", -1)) {
        if (!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"))
          throw new IllegalArgumentException("Invalid verification mailbox");
      }
    } catch (AddressException failure) {
      // AddressException retains the original input; never expose it or its cause.
      throw new IllegalArgumentException("Invalid verification mailbox");
    }
  }

  @Override
  public String toString() {
    return "VerificationMailbox[redacted]";
  }
}
