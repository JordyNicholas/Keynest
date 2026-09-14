package dev.keynest.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds UI-facing copy for a {@link PasswordPolicy}.
 */
public final class PolicyExplainer {

    private PolicyExplainer() {
    }

    public static PolicyExplanation explain(PasswordPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        List<PolicyExplanation.ExplainedRule> rules = new ArrayList<>();

        rules.add(rule(
                "password.length",
                "Must be between " + policy.minLength() + " and " + policy.maxLength() + " characters."));

        if (policy.requireUpper()) {
            rules.add(rule("password.require_upper", "Must include at least one uppercase letter."));
        }
        if (policy.requireLower()) {
            rules.add(rule("password.require_lower", "Must include at least one lowercase letter."));
        }
        if (policy.requireDigit()) {
            rules.add(rule("password.require_digit", "Must include at least one digit."));
        }
        if (policy.requireSymbol()) {
            rules.add(rule("password.require_symbol", "Must include at least one symbol."));
        }
        if (policy.minCategories() > 0) {
            rules.add(rule(
                    "password.min_categories",
                    "Must use at least " + policy.minCategories()
                            + " character categories (upper, lower, digit, symbol)."));
        }
        if (policy.excludeAmbiguous()) {
            rules.add(rule(
                    "password.ambiguous_chars",
                    "Must not use ambiguous characters (" + PasswordValidator.AMBIGUOUS + ")."));
        }
        if (policy.forbidWhitespace()) {
            rules.add(rule("password.whitespace_forbidden", "Must not contain whitespace."));
        }
        if (policy.forbidUsernameSubstring()) {
            rules.add(rule(
                    "password.contains_username",
                    "Must not contain the username or email local-part (3+ characters)."));
        }
        if (policy.maxAgeDays() != null) {
            rules.add(rule(
                    "password.max_age_days",
                    "Suggested rotation window: " + policy.maxAgeDays() + " days."));
        }
        if (!policy.bannedSubstrings().isEmpty()) {
            String listed = policy.bannedSubstrings().stream()
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(", "));
            rules.add(rule(
                    "password.banned_substring",
                    "Must not contain banned substrings (case-insensitive): " + listed + "."));
        }
        if (!policy.bannedRegexes().isEmpty()) {
            String listed = policy.bannedRegexes().stream()
                    .map(s -> "/" + s + "/")
                    .collect(Collectors.joining(", "));
            rules.add(rule(
                    "password.banned_regex",
                    "Must not match banned patterns: " + listed + "."));
        }

        return new PolicyExplanation(policy.id(), rules);
    }

    private static PolicyExplanation.ExplainedRule rule(String code, String message) {
        return new PolicyExplanation.ExplainedRule(code, message);
    }
}
