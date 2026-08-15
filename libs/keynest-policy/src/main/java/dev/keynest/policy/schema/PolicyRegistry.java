package dev.keynest.policy.schema;

import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PolicyOperation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Skeleton registry of schema-backed password policies.
 * Manager and external services resolve policies by id before generate/update.
 */
public final class PolicyRegistry {

    private final Map<String, PasswordPolicyDocument> byId;

    public PolicyRegistry(Collection<PasswordPolicyDocument> documents) {
        Objects.requireNonNull(documents, "documents");
        Map<String, PasswordPolicyDocument> map = new LinkedHashMap<>();
        for (PasswordPolicyDocument document : documents) {
            if (map.put(document.id(), document) != null) {
                throw new PolicySchemaException("duplicate policy id: " + document.id());
            }
        }
        this.byId = Map.copyOf(map);
    }

    public static PolicyRegistry withDefaults() {
        return new PolicyRegistry(java.util.List.of(PasswordPolicyLoader.loadDefault()));
    }

    public Optional<PasswordPolicyDocument> find(String policyId) {
        return Optional.ofNullable(byId.get(policyId));
    }

    public PasswordPolicyDocument require(String policyId) {
        return find(policyId).orElseThrow(() -> new PolicySchemaException("unknown policy id: " + policyId));
    }

    public PasswordPolicyDocument requireFor(String policyId, PolicyOperation operation) {
        PasswordPolicyDocument document = require(policyId);
        if (!document.supports(operation)) {
            throw new PolicySchemaException(
                    "policy " + policyId + " does not apply to operation " + operation.wireValue()
            );
        }
        return document;
    }

    public PasswordPolicy runtime(String policyId) {
        return require(policyId).toRuntimePolicy();
    }

    public Map<String, PasswordPolicyDocument> all() {
        return byId;
    }
}
