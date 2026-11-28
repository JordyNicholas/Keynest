package dev.keynest.manager.auth.verification;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

/** Installation credentials remain private application memory, never a DTO or generic mail property map. */
final class VerificationMailSettings {
  final VerificationMail.Readiness readiness;
  final String host;
  final int port;
  final String username;
  final String password;
  final String sender;
  final String origin;
  final boolean implicitTls;

  private VerificationMailSettings(VerificationMail.Readiness readiness, String host, int port, String username,
    String password, String sender, String origin, boolean implicitTls) {
    this.readiness = readiness;
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
    this.sender = sender;
    this.origin = origin;
    this.implicitTls = implicitTls;
  }

  static VerificationMailSettings read(Function<String, String> properties) {
    try {
      String[] names = {"host", "port", "username", "password", "sender", "web-origin", "tls-mode"};
      String[] values = new String[names.length];
      int supplied = 0;
      for (int i = 0; i < names.length; i++) {
        values[i] = properties.apply("keynest.verification-mail." + names[i]);
        if (values[i] != null && !values[i].isEmpty())
          supplied++;
      }
      if (supplied == 0)
        return disabled(VerificationMail.Readiness.UNCONFIGURED);
      if (supplied != values.length)
        return disabled(VerificationMail.Readiness.INVALID);
      for (String value : values) {
        if (value.isBlank() || value.length() > 4096 || value.chars().anyMatch(c -> c < 32 || c == 127))
          return disabled(VerificationMail.Readiness.INVALID);
      }
      String host = values[0];
      URI server = URI.create("smtp://" + host);
      if (host.length() > 253 || server.getHost() == null || !server.getHost().equals(host)
        || server.getRawUserInfo() != null || server.getPort() != -1 || !server.getRawPath().isEmpty()
        || server.getRawQuery() != null || server.getRawFragment() != null)
        return disabled(VerificationMail.Readiness.INVALID);
      int port = Integer.parseInt(values[1]);
      if (port < 1 || port > 65535)
        return disabled(VerificationMail.Readiness.INVALID);
      String sender = VerificationMailbox.parse(values[4]).delivery();
      URI web = URI.create(values[5]);
      if (!"https".equals(web.getScheme()) || web.getHost() == null || web.getRawUserInfo() != null
        || web.getRawQuery() != null || web.getRawFragment() != null
        || !(web.getRawPath().isEmpty() || web.getRawPath().equals("/")) || web.getPort() < -1 || web.getPort() == 0
        || web.getPort() > 65535)
        return disabled(VerificationMail.Readiness.INVALID);
      String origin = new URI("https", null, web.getHost(), web.getPort(), null, null, null).toASCIIString();
      if (!values[6].equals("STARTTLS") && !values[6].equals("IMPLICIT_TLS"))
        return disabled(VerificationMail.Readiness.INVALID);
      return new VerificationMailSettings(VerificationMail.Readiness.READY, host, port, values[2], values[3], sender,
        origin, values[6].equals("IMPLICIT_TLS"));
    } catch (RuntimeException | URISyntaxException failure) {
      // Invalid deployment values must not take existing sign-in/Vault down or expose binding exceptions.
      return disabled(VerificationMail.Readiness.INVALID);
    }
  }

  private static VerificationMailSettings disabled(VerificationMail.Readiness reason) {
    return new VerificationMailSettings(reason, "", 0, "", "", "", "", false);
  }

  @Override
  public String toString() {
    return "VerificationMailSettings[redacted]";
  }
}
