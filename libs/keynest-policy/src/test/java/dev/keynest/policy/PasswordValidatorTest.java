package dev.keynest.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordValidatorTest {

    private final PasswordPolicy policy = PasswordPolicy.defaultPolicy();

    @Test
    void acceptsStrongPassword() {
        ValidationResult result = PasswordValidator.validate("Tr7ub4dor$Xy9#Qm", policy);
        assertTrue(result.valid(), () -> result.violations().toString());
    }

    @Test
    void rejectsTooShort() {
        ValidationResult result = PasswordValidator.validate("Ab2!short", policy);
        assertFalse(result.valid());
        assertTrue(result.violations().contains("password.too_short"));
    }

    @Test
    void rejectsUsernameSubstring() {
        ValidationResult result = PasswordValidator.validate("JordyNicholas!A2bcdef", policy, "jordy@example.com");
        assertFalse(result.valid());
        assertTrue(result.violations().contains("password.contains_username"));
    }
}
