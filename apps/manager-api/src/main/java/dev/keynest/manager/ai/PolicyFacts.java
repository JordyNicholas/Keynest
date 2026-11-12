package dev.keynest.manager.ai;

import dev.keynest.policy.PasswordPolicy;

/** Only approved built-in scalar facts; no policy text, IDs, denylist or context. */
public record PolicyFacts(int minLength, int maxLength, boolean requireUpper, boolean requireLower,
  boolean requireDigit, boolean requireSymbol, int minCategories, boolean excludeAmbiguous,
  boolean forbidWhitespace, boolean forbidUsernameSubstring, Integer maxAgeDays) {

  public static PolicyFacts from(PasswordPolicy policy) {
    return new PolicyFacts(policy.minLength(), policy.maxLength(), policy.requireUpper(), policy.requireLower(),
      policy.requireDigit(), policy.requireSymbol(), policy.minCategories(), policy.excludeAmbiguous(),
      policy.forbidWhitespace(), policy.forbidUsernameSubstring(), policy.maxAgeDays());
  }
}
