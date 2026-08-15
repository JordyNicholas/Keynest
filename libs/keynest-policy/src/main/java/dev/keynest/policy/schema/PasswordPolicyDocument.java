package dev.keynest.policy.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PolicyOperation;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Parsed password-policy document (JSON). Source of truth for generate/create/update/rotate.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public final class PasswordPolicyDocument {

    private String id;
    private int version;
    private String description;
    private List<String> appliesTo;
    private Rules rules;

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int version() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> appliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(List<String> appliesTo) {
        this.appliesTo = appliesTo;
    }

    public Rules rules() {
        return rules;
    }

    public void setRules(Rules rules) {
        this.rules = rules;
    }

    public Set<PolicyOperation> operations() {
        EnumSet<PolicyOperation> ops = EnumSet.noneOf(PolicyOperation.class);
        if (appliesTo != null) {
            for (String value : appliesTo) {
                ops.add(PolicyOperation.fromWire(value));
            }
        }
        return Set.copyOf(ops);
    }

    public boolean supports(PolicyOperation operation) {
        return operations().contains(operation);
    }

    public PasswordPolicy toRuntimePolicy() {
        Objects.requireNonNull(rules, "rules");
        return PasswordPolicy.builder()
                .id(id)
                .minLength(rules.minLength)
                .maxLength(rules.maxLength)
                .requireUpper(rules.requireUpper)
                .requireLower(rules.requireLower)
                .requireDigit(rules.requireDigit)
                .requireSymbol(rules.requireSymbol)
                .minCategories(rules.minCategories)
                .excludeAmbiguous(rules.excludeAmbiguous)
                .forbidWhitespace(rules.forbidWhitespace)
                .forbidUsernameSubstring(rules.forbidUsernameSubstring)
                .maxAgeDays(rules.maxAgeDays)
                .bannedSubstrings(rules.bannedSubstrings == null ? List.of() : rules.bannedSubstrings)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class Rules {
        public int minLength;
        public int maxLength;
        public boolean requireUpper;
        public boolean requireLower;
        public boolean requireDigit;
        public boolean requireSymbol;
        public int minCategories;
        public boolean excludeAmbiguous;
        public boolean forbidWhitespace;
        public boolean forbidUsernameSubstring;
        public Integer maxAgeDays;
        public List<String> bannedSubstrings;
    }
}
