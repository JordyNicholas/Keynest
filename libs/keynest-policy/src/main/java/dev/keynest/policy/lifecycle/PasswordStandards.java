package dev.keynest.policy.lifecycle;

import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PasswordValidator;
import dev.keynest.policy.PolicyOperation;
import dev.keynest.policy.ValidationResult;
import dev.keynest.policy.schema.PasswordPolicyDocument;
import dev.keynest.policy.schema.PolicyRegistry;
import dev.keynest.policy.schema.PolicySchemaException;

import java.util.Objects;

/**
 * Skeleton façade: every create/update/generate path resolves a schema-backed policy first.
 * Generation itself lives in {@code keynest-generate}; this type owns policy gating + validate.
 */
public final class PasswordStandards {

    private final PolicyRegistry registry;

    public PasswordStandards(PolicyRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static PasswordStandards withDefaults() {
        return new PasswordStandards(PolicyRegistry.withDefaults());
    }

    public PolicyRegistry registry() {
        return registry;
    }

    public PasswordPolicyDocument policyFor(String policyId, PolicyOperation operation) {
        return registry.requireFor(policyId, operation);
    }

    public ValidationResult validateForCreate(String policyId, String password, String usernameOrEmail) {
        return validate(policyId, PolicyOperation.CREATE, password, usernameOrEmail);
    }

    public ValidationResult validateForUpdate(String policyId, String password, String usernameOrEmail) {
        return validate(policyId, PolicyOperation.UPDATE, password, usernameOrEmail);
    }

    public ValidationResult validateForRotate(String policyId, String password, String usernameOrEmail) {
        return validate(policyId, PolicyOperation.ROTATE, password, usernameOrEmail);
    }

    public ValidationResult validateForGenerateCheck(String policyId, String password) {
        return validate(policyId, PolicyOperation.GENERATE, password, null);
    }

    public PasswordPolicy runtimePolicy(String policyId, PolicyOperation operation) {
        return policyFor(policyId, operation).toRuntimePolicy();
    }

    /**
     * Skeleton hook for Manager update flows: ensure candidate is allowed under the item's policy.
     * Full rotate/notify orchestration stays in Manager.
     */
    public void assertValidForUpdate(String policyId, String password, String usernameOrEmail) {
        ValidationResult result = validateForUpdate(policyId, password, usernameOrEmail);
        if (!result.valid()) {
            throw new PolicySchemaException("password failed update policy " + policyId + ": " + result.violations());
        }
    }

    private ValidationResult validate(
            String policyId,
            PolicyOperation operation,
            String password,
            String usernameOrEmail
    ) {
        PasswordPolicy policy = runtimePolicy(policyId, operation);
        return PasswordValidator.validate(password, policy, usernameOrEmail);
    }
}
