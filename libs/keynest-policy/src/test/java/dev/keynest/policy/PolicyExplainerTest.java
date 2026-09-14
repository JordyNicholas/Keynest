package dev.keynest.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyExplainerTest {

    @Test
    void explainDefaultPolicyIncludesLengthAndDenylist() {
        PasswordPolicy policy = PasswordPolicy.defaultPolicy();
        PolicyExplanation explanation = PolicyExplainer.explain(policy);

        assertEquals(policy.id(), explanation.policyId());
        assertTrue(explanation.rules().stream().anyMatch(r -> r.code().equals("password.length")));
        assertTrue(explanation.rules().stream().anyMatch(r -> r.code().equals("password.banned_substring")));
        assertTrue(explanation.rules().stream().anyMatch(r -> r.code().equals("password.banned_regex")));
        assertTrue(explanation.asText().contains("16"));
        assertTrue(explanation.asText().contains("password"));
    }

    @Test
    void explainOmitsOptionalRulesWhenDisabled() {
        PasswordPolicy policy = PasswordPolicy.builder()
                .minLength(8)
                .maxLength(32)
                .requireUpper(false)
                .requireLower(true)
                .requireDigit(false)
                .requireSymbol(false)
                .minCategories(0)
                .excludeAmbiguous(false)
                .forbidWhitespace(false)
                .forbidUsernameSubstring(false)
                .maxAgeDays(null)
                .bannedSubstrings(List.of())
                .bannedRegexes(List.of())
                .build();

        PolicyExplanation explanation = PolicyExplainer.explain(policy);
        assertEquals(2, explanation.rules().size()); // length + require_lower
        assertTrue(explanation.rules().stream().anyMatch(r -> r.code().equals("password.require_lower")));
        assertFalse(explanation.rules().stream().anyMatch(r -> r.code().equals("password.require_upper")));
    }

    @Test
    void rejectInvalidBannedRegexAtBuildTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PasswordPolicy.builder().bannedRegexes(List.of("(")).build());
    }
}
