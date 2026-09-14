package dev.keynest.policy;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Human-readable explanation of a {@link PasswordPolicy} for UI copy.
 */
public final class PolicyExplanation {

    private final String policyId;
    private final List<ExplainedRule> rules;

    public PolicyExplanation(String policyId, List<ExplainedRule> rules) {
        this.policyId = Objects.requireNonNull(policyId, "policyId");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public String policyId() {
        return policyId;
    }

    public List<ExplainedRule> rules() {
        return rules;
    }

    /** Newline-joined rule messages for simple text UIs. */
    public String asText() {
        return rules.stream().map(ExplainedRule::message).collect(Collectors.joining("\n"));
    }

    public record ExplainedRule(String code, String message) {
        public ExplainedRule {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
