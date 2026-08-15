package dev.keynest.policy;

/**
 * Operations that consume a password policy schema.
 * Must stay aligned with {@code appliesTo} enum values in {@code password-policy.schema.json}.
 */
public enum PolicyOperation {
    GENERATE,
    CREATE,
    UPDATE,
    ROTATE;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static PolicyOperation fromWire(String value) {
        return PolicyOperation.valueOf(value.trim().toUpperCase());
    }
}
