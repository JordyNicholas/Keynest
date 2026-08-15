package dev.keynest.policy;

import java.util.Objects;

/**
 * Declarative password standards. Used by both {@code validate} and {@code generate}.
 */
public final class PasswordPolicy {

    public static final String DEFAULT_POLICY_ID = "keynest.default.v1";

    private final String id;
    private final int minLength;
    private final int maxLength;
    private final boolean requireUpper;
    private final boolean requireLower;
    private final boolean requireDigit;
    private final boolean requireSymbol;
    private final int minCategories;
    private final boolean excludeAmbiguous;
    private final boolean forbidWhitespace;

    private PasswordPolicy(Builder builder) {
        this.id = builder.id;
        this.minLength = builder.minLength;
        this.maxLength = builder.maxLength;
        this.requireUpper = builder.requireUpper;
        this.requireLower = builder.requireLower;
        this.requireDigit = builder.requireDigit;
        this.requireSymbol = builder.requireSymbol;
        this.minCategories = builder.minCategories;
        this.excludeAmbiguous = builder.excludeAmbiguous;
        this.forbidWhitespace = builder.forbidWhitespace;
    }

    public static PasswordPolicy defaultPolicy() {
        return builder()
                .id(DEFAULT_POLICY_ID)
                .minLength(16)
                .maxLength(128)
                .requireUpper(true)
                .requireLower(true)
                .requireDigit(true)
                .requireSymbol(true)
                .minCategories(3)
                .excludeAmbiguous(true)
                .forbidWhitespace(true)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String id() {
        return id;
    }

    public int minLength() {
        return minLength;
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean requireUpper() {
        return requireUpper;
    }

    public boolean requireLower() {
        return requireLower;
    }

    public boolean requireDigit() {
        return requireDigit;
    }

    public boolean requireSymbol() {
        return requireSymbol;
    }

    public int minCategories() {
        return minCategories;
    }

    public boolean excludeAmbiguous() {
        return excludeAmbiguous;
    }

    public boolean forbidWhitespace() {
        return forbidWhitespace;
    }

    public static final class Builder {
        private String id = DEFAULT_POLICY_ID;
        private int minLength = 16;
        private int maxLength = 128;
        private boolean requireUpper = true;
        private boolean requireLower = true;
        private boolean requireDigit = true;
        private boolean requireSymbol = true;
        private int minCategories = 3;
        private boolean excludeAmbiguous = true;
        private boolean forbidWhitespace = true;

        public Builder id(String id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder requireUpper(boolean requireUpper) {
            this.requireUpper = requireUpper;
            return this;
        }

        public Builder requireLower(boolean requireLower) {
            this.requireLower = requireLower;
            return this;
        }

        public Builder requireDigit(boolean requireDigit) {
            this.requireDigit = requireDigit;
            return this;
        }

        public Builder requireSymbol(boolean requireSymbol) {
            this.requireSymbol = requireSymbol;
            return this;
        }

        public Builder minCategories(int minCategories) {
            this.minCategories = minCategories;
            return this;
        }

        public Builder excludeAmbiguous(boolean excludeAmbiguous) {
            this.excludeAmbiguous = excludeAmbiguous;
            return this;
        }

        public Builder forbidWhitespace(boolean forbidWhitespace) {
            this.forbidWhitespace = forbidWhitespace;
            return this;
        }

        public PasswordPolicy build() {
            if (minLength < 1) {
                throw new IllegalArgumentException("minLength must be >= 1");
            }
            if (maxLength < minLength) {
                throw new IllegalArgumentException("maxLength must be >= minLength");
            }
            if (minCategories < 0 || minCategories > 4) {
                throw new IllegalArgumentException("minCategories must be between 0 and 4");
            }
            return new PasswordPolicy(this);
        }
    }
}
