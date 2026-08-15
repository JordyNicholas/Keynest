package dev.keynest.policy.schema;

import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PolicyOperation;
import dev.keynest.policy.lifecycle.PasswordStandards;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyLoaderTest {

    @Test
    void loadsAndValidatesDefaultPolicyAgainstJsonSchema() {
        PasswordPolicyDocument document = PasswordPolicyLoader.loadDefault();
        assertEquals("keynest.default.v1", document.id());
        assertEquals(1, document.version());
        assertTrue(document.supports(PolicyOperation.GENERATE));
        assertTrue(document.supports(PolicyOperation.UPDATE));

        PasswordPolicy policy = document.toRuntimePolicy();
        assertEquals(16, policy.minLength());
        assertEquals(90, policy.maxAgeDays());
        assertTrue(policy.forbidUsernameSubstring());
    }

    @Test
    void defaultPolicyFactoryUsesSchemaDocument() {
        PasswordPolicy policy = PasswordPolicy.defaultPolicy();
        assertEquals(PasswordPolicy.DEFAULT_POLICY_ID, policy.id());
        assertEquals(16, policy.minLength());
    }

    @Test
    void registryRequiresOperationSupport() {
        PolicyRegistry registry = PolicyRegistry.withDefaults();
        assertEquals("keynest.default.v1", registry.requireFor("keynest.default.v1", PolicyOperation.CREATE).id());
    }

    @Test
    void standardsValidateUpdateAgainstSchemaPolicy() {
        PasswordStandards standards = PasswordStandards.withDefaults();
        assertTrue(standards.validateForUpdate("keynest.default.v1", "Tr7ub4dor$Xy9#Qm", null).valid());
        assertFalse(standards.validateForUpdate("keynest.default.v1", "short", null).valid());
        assertThrows(
                PolicySchemaException.class,
                () -> standards.assertValidForUpdate("keynest.default.v1", "short", null)
        );
    }

    @Test
    void rejectsUnknownFieldsInPolicyDocument() {
        String invalid = """
                {
                  "id": "bad.policy.v1",
                  "version": 1,
                  "appliesTo": ["generate"],
                  "rules": {
                    "minLength": 8,
                    "maxLength": 32,
                    "requireUpper": true,
                    "requireLower": true,
                    "requireDigit": true,
                    "requireSymbol": false,
                    "minCategories": 2,
                    "excludeAmbiguous": false,
                    "forbidWhitespace": true,
                    "forbidUsernameSubstring": false,
                    "extraField": true
                  }
                }
                """;
        assertThrows(
                PolicySchemaException.class,
                () -> PasswordPolicyLoader.load(new java.io.ByteArrayInputStream(invalid.getBytes()))
        );
    }
}
