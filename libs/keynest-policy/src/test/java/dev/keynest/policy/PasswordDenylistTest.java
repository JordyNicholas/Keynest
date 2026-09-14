package dev.keynest.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordDenylistTest {

    @Test
    void rejectsBannedSubstringCaseInsensitive() {
        PasswordPolicy policy = PasswordPolicy.builder()
                .bannedSubstrings(List.of("password"))
                .bannedRegexes(List.of())
                .build();

        ValidationResult result = PasswordValidator.validate("XxPassword99!$abcd", policy);
        assertFalse(result.valid());
        assertTrue(result.violations().contains("password.banned_substring"));
    }

    @Test
    void rejectsBannedRegexFourRepeatedChars() {
        PasswordPolicy policy = PasswordPolicy.builder()
                .bannedSubstrings(List.of())
                .bannedRegexes(List.of("(.)\\1{3,}"))
                .build();

        ValidationResult rejected = PasswordValidator.validate("Abcd2222!xyzEFGH", policy);
        assertFalse(rejected.valid());
        assertTrue(rejected.violations().contains("password.banned_regex"));

        ValidationResult accepted = PasswordValidator.validate("Abcd2e3f!xyzEFGH", policy);
        assertTrue(accepted.valid(), () -> accepted.violations().toString());
    }

    @Test
    void defaultPolicyDocumentsDenylistAgainstSchema() {
        PasswordPolicy policy = PasswordPolicy.defaultPolicy();
        assertFalse(policy.bannedSubstrings().isEmpty());
        assertFalse(policy.bannedRegexes().isEmpty());

        ValidationResult bannedWord = PasswordValidator.validate("AdminUser!Tr7ubadorX", policy);
        assertFalse(bannedWord.valid());
        assertTrue(bannedWord.violations().contains("password.banned_substring"));
    }
}
