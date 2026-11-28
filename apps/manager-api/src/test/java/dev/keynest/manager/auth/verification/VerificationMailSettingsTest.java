package dev.keynest.manager.auth.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VerificationMailSettingsTest {
  @Test
  void mailboxParserPreservesDeliverySpellingWithoutProviderAliasRewriting() {
    var address = VerificationMailbox.parse("  First.Last+Tag@Example.Invalid  ");
    assertThat(address.delivery()).isEqualTo("First.Last+Tag@Example.Invalid");
    assertThat(address.identity()).isEqualTo("first.last+tag@example.invalid");
    assertThat(address.toString()).isEqualTo("VerificationMailbox[redacted]");
    assertThat(VerificationMailbox.parse("\"local part\"@example.invalid").identity())
      .isEqualTo("\"local part\"@example.invalid");
  }

  @Test
  void rejectsListsDisplayNamesCommentsControlsLiteralsUnicodeAndMalformedSyntaxWithoutLeakingInput() {
    for (String invalid : new String[]{"", "x", "a@@example.invalid", "a..b@example.invalid", ".a@example.invalid",
      "a@example.invalid, b@example.invalid", "Name <a@example.invalid>", "<a@example.invalid>",
      "a(comment)@example.invalid", "a@example.invalid (comment)", "group:a@example.invalid;", "a@[127.0.0.1]",
      "é@example.invalid", "a@éxample.invalid", "a@-example.invalid", "a@example-.invalid", "a@example..invalid",
      "a@example.invalid.", "a\r\n@example.invalid", "\ta@example.invalid", "a@example.invalid\u007f"}) {
      assertThatThrownBy(() -> VerificationMailbox.parse(invalid))
        .isInstanceOfSatisfying(IllegalArgumentException.class, failure -> {
          assertThat(failure.getMessage()).isEqualTo("Invalid verification mailbox");
          assertThat(failure.getCause()).isNull();
        });
    }
    assertThatThrownBy(() -> VerificationMailbox.parse(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VerificationMailbox("different@example.invalid", "a@example.invalid"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void boundsMailboxLocalPartDomainLabelsAndCompleteLengthBeforeAllocatingMail() {
    String local = "x".repeat(64);
    String domain = "d".repeat(63) + "." + "d".repeat(63) + "." + "d".repeat(61);
    assertThat((local + "@" + domain).length()).isEqualTo(254);
    assertThat(VerificationMailbox.parse(local + "@" + domain).delivery().length()).isEqualTo(254);
    for (String invalid : new String[]{"x".repeat(65) + "@example.invalid", local + "@" + domain + "x",
      "a@" + "d".repeat(64) + ".invalid", "x".repeat(321)})
      assertThatThrownBy(() -> VerificationMailbox.parse(invalid)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingPartialAndMalformedMailConfigRemainUnavailableWithoutNetworkOrStartupFailure() {
    assertThat(VerificationMailSettings.read(name -> null).readiness)
      .isEqualTo(VerificationMail.Readiness.UNCONFIGURED);
    assertThat(VerificationMailSettings.read(name -> "").readiness).isEqualTo(VerificationMail.Readiness.UNCONFIGURED);
    assertThat(VerificationMailSettings.read(name -> {
      throw new IllegalStateException("Private deployment value");
    }).readiness).isEqualTo(VerificationMail.Readiness.INVALID);
    var values = validSettings(587, false);
    for (String key : values.keySet()) {
      var partial = new HashMap<>(values);
      partial.remove(key);
      assertThat(settings(partial).readiness).isEqualTo(VerificationMail.Readiness.INVALID);
    }
    for (String invalid : new String[]{"0", "65536", "invalid"}) {
      var bad = new HashMap<>(values);
      bad.put("port", invalid);
      assertThat(settings(bad).readiness).isEqualTo(VerificationMail.Readiness.INVALID);
    }
    for (String invalid : new String[]{"smtp://example.invalid", "host/path", "user@host", "host\r\n"}) {
      var bad = new HashMap<>(values);
      bad.put("host", invalid);
      assertThat(settings(bad).readiness).isEqualTo(VerificationMail.Readiness.INVALID);
    }
    for (String invalid : new String[]{"NONE", "", " ", "x".repeat(4097), "x\n"}) {
      var bad = new HashMap<>(values);
      bad.put("tls-mode", invalid);
      assertThat(settings(bad).readiness).isEqualTo(VerificationMail.Readiness.INVALID);
    }
  }

  @Test
  void requiresFixedHttpsOriginWithoutCredentialsPathQueryFragmentOrInvalidPorts() {
    var values = validSettings(587, false);
    for (String origin : new String[]{"http://example.invalid", "https://user:password@example.invalid",
      "https://example.invalid/path", "https://example.invalid?query", "https://example.invalid#fragment",
      "https://example.invalid:0", "https://example.invalid:65536", "https://example.invalid:-2", "https:relative",
      "//example.invalid", "https://éxample.invalid", "https://example.invalid/%2f"}) {
      var bad = new HashMap<>(values);
      bad.put("web-origin", origin);
      assertThat(settings(bad).readiness).isEqualTo(VerificationMail.Readiness.INVALID);
    }
    values.put("web-origin", "https://example.invalid:8443/");
    var ready = settings(values);
    assertThat(ready.readiness).isEqualTo(VerificationMail.Readiness.READY);
    assertThat(ready.origin).isEqualTo("https://example.invalid:8443");
    assertThat(ready.toString()).isEqualTo("VerificationMailSettings[redacted]");
  }

  static Map<String, String> validSettings(int port, boolean implicit) {
    return new HashMap<>(Map.of("host", "localhost", "port", Integer.toString(port), "username", "fixture-user",
      "password", "disposable-fixture-password", "sender", "Keynest@Example.Invalid", "web-origin",
      "https://keynest.example.invalid", "tls-mode", implicit ? "IMPLICIT_TLS" : "STARTTLS"));
  }
  static VerificationMailSettings settings(Map<String, String> values) {
    return VerificationMailSettings.read(name -> values.get(name.substring("keynest.verification-mail.".length())));
  }
}
