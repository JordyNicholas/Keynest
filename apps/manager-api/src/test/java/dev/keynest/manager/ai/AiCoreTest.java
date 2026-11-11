package dev.keynest.manager.ai;

import dev.keynest.manager.config.AiProperties;
import dev.keynest.policy.PasswordPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCoreTest {
  static final String KEY = "synthetic-provider-credential-sentinel";

  static final class Fake implements AiProvider {
    int calls;
    PolicyFacts facts;
    ProviderCredential lastCredential;
    String output = "Use the selected character categories and length bounds.";
    RuntimeException failure;
    @Override public String id() { return "test"; }
    @Override public String explain(PolicyFacts facts, ProviderCredential credential) {
      calls++;
      this.facts = facts;
      lastCredential = credential;
      if (failure != null) throw failure;
      return output;
    }
  }

  @Test
  void credentialIsBoundedRedactedAndCleared() {
    try (var credential = new ProviderCredential(KEY)) {
      assertThat(credential.toString()).isEqualTo("ProviderCredential[REDACTED]");
      assertThat(credential.appearsIn("prefix" + KEY + "suffix")).isTrue();
      assertThat(credential.transportValue()).isEqualTo(KEY);
      credential.close();
      assertThatThrownBy(credential::transportValue).isInstanceOf(AiFailure.class);
    }
    for (String invalid : List.of("\n", "with space", "non-ascii-é", "x".repeat(4097))) {
      assertThatThrownBy(() -> new ProviderCredential(invalid)).isInstanceOf(AiFailure.class)
        .hasMessage("AI_CREDENTIAL_INVALID");
    }
    assertThatThrownBy(() -> new ProviderCredential(null)).hasMessage("AI_CREDENTIAL_REQUIRED");
  }

  @Test
  void disabledAndInvalidInputsNeverCallTheProvider() {
    var fake = new Fake();
    var properties = new AiProperties();
    var service = new AiExplanationService(properties, List.of(fake), PasswordPolicy.defaultPolicy(), new AiAdmission());
    UUID user = UUID.randomUUID();
    assertThatThrownBy(() -> service.explain(user, "test", PasswordPolicy.DEFAULT_POLICY_ID, KEY)).hasMessage("AI_DISABLED");
    properties.setEnabled(true);
    assertThatThrownBy(() -> service.explain(user, "unknown", PasswordPolicy.DEFAULT_POLICY_ID, KEY)).hasMessage("AI_PROVIDER_UNSUPPORTED");
    assertThatThrownBy(() -> service.explain(user, "test", "private-policy", KEY)).hasMessage("AI_POLICY_UNSUPPORTED");
    assertThatThrownBy(() -> service.explain(user, "test", PasswordPolicy.DEFAULT_POLICY_ID, "bad\nkey")).hasMessage("AI_CREDENTIAL_INVALID");
    assertThat(fake.calls).isZero();
  }

  @Test
  void onlyScalarFactsReachTheProviderAndCredentialsAreClosed() throws Exception {
    var fake = new Fake();
    var properties = new AiProperties();
    properties.setEnabled(true);
    var service = new AiExplanationService(properties, List.of(fake), PasswordPolicy.defaultPolicy(), new AiAdmission());
    var result = service.explain(UUID.randomUUID(), "test", PasswordPolicy.DEFAULT_POLICY_ID, KEY);
    assertThat(result.explanation()).isEqualTo(fake.output);
    var facts = AiJson.MAPPER.valueToTree(fake.facts);
    assertThat(facts.size()).isEqualTo(11);
    assertThat(facts.get("minLength").intValue()).isEqualTo(16);
    assertThat(facts.toString()).doesNotContain(KEY, "description", "banned", "policyId", "user", "envelope");
    assertThatThrownBy(fake.lastCredential::transportValue).hasMessage("AI_CREDENTIAL_INVALID");
  }

  @Test
  void invalidReflectedAndFailedOutputsAreSanitized() {
    var fake = new Fake();
    var properties = new AiProperties();
    properties.setEnabled(true);
    var service = new AiExplanationService(properties, List.of(fake), PasswordPolicy.defaultPolicy(), new AiAdmission());
    for (String invalid : List.of(" ", "x".repeat(4001), "echo " + KEY)) {
      fake.output = invalid;
      assertThatThrownBy(() -> service.explain(UUID.randomUUID(), "test", PasswordPolicy.DEFAULT_POLICY_ID, KEY))
        .hasMessage("AI_INVALID_OUTPUT").hasNoCause();
    }
    fake.failure = new IllegalStateException(KEY);
    assertThatThrownBy(() -> service.explain(UUID.randomUUID(), "test", PasswordPolicy.DEFAULT_POLICY_ID, KEY))
      .hasMessage("AI_UNAVAILABLE").hasNoCause();
    assertThatThrownBy(fake.lastCredential::transportValue).hasMessage("AI_CREDENTIAL_INVALID");
  }

  @Test
  void perUserAndProcessRatesAreBoundedAndReset() {
    var clock = new MutableClock();
    var admission = new AiAdmission(clock);
    UUID user = UUID.randomUUID();
    for (int i = 0; i < 5; i++) try (var ignored = admission.acquire(user)) {}
    assertThatThrownBy(() -> admission.acquire(user)).hasMessage("AI_RATE_LIMITED");
    for (int i = 0; i < 15; i++) try (var ignored = admission.acquire(UUID.randomUUID())) {}
    assertThatThrownBy(() -> admission.acquire(UUID.randomUUID())).hasMessage("AI_RATE_LIMITED");
    clock.millis += 60_000;
    try (var ignored = admission.acquire(user)) {}
  }

  @Test
  void concurrencyHasNoQueueAndLeaseReleaseIsIdempotent() {
    var admission = new AiAdmission();
    var first = admission.acquire(UUID.randomUUID());
    var second = admission.acquire(UUID.randomUUID());
    assertThatThrownBy(() -> admission.acquire(UUID.randomUUID())).hasMessage("AI_CAPACITY_LIMITED");
    first.close();
    first.close();
    var third = admission.acquire(UUID.randomUUID());
    assertThatThrownBy(() -> admission.acquire(UUID.randomUUID())).hasMessage("AI_CAPACITY_LIMITED");
    second.close();
    third.close();
  }

  private static final class MutableClock extends Clock {
    long millis = 120_000;
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
  }
}
