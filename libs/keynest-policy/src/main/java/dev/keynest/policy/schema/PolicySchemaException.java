package dev.keynest.policy.schema;

public final class PolicySchemaException extends RuntimeException {

    public PolicySchemaException(String message) {
        super(message);
    }

    public PolicySchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
