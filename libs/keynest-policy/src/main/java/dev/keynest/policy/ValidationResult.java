package dev.keynest.policy;

import java.util.List;
import java.util.Objects;

public final class ValidationResult {

    private final boolean valid;
    private final List<String> violations;

    private ValidationResult(boolean valid, List<String> violations) {
        this.valid = valid;
        this.violations = List.copyOf(violations);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failed(List<String> violations) {
        Objects.requireNonNull(violations, "violations");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("failed result requires at least one violation");
        }
        return new ValidationResult(false, violations);
    }

    public boolean valid() {
        return valid;
    }

    public List<String> violations() {
        return violations;
    }
}
