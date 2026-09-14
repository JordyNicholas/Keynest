package dev.keynest.generate;

import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PasswordValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordGeneratorTest {

    @Test
    void generatedPasswordPassesSamePolicy() {
        PasswordPolicy policy = PasswordPolicy.defaultPolicy();
        PasswordGenerator generator = new PasswordGenerator();
        for (int i = 0; i < 20; i++) {
            String password = generator.generate(policy);
            assertTrue(PasswordValidator.validate(password, policy).valid(), password);
        }
    }

    @Test
    void generatedPasswordRespectsCustomDenylist() {
        PasswordPolicy policy = PasswordPolicy.builder()
                .bannedSubstrings(java.util.List.of("password", "admin"))
                .bannedRegexes(java.util.List.of("(.)\\1{3,}"))
                .build();
        PasswordGenerator generator = new PasswordGenerator();
        String password = generator.generate(policy);
        assertTrue(PasswordValidator.validate(password, policy).valid(), password);
    }
}
