package dev.keynest.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Runtime password standards. Prefer loading from a policy document via
 * {@link dev.keynest.policy.schema.PasswordPolicyLoader} so generate/update share one schema.
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
    private final boolean forbidUsernameSubstring;
    private final Integer maxAgeDays;
    private final List<String> bannedSubstrings;
    private final List<String> bannedRegexes;
    private final List<Pattern> bannedPatterns;

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
        this.forbidUsernameSubstring = builder.forbidUsernameSubstring;
        this.maxAgeDays = builder.maxAgeDays;
        this.bannedSubstrings = List.copyOf(builder.bannedSubstrings);
        this.bannedRegexes = List.copyOf(builder.bannedRegexes);
        this.bannedPatterns = List.copyOf(builder.bannedPatterns);
    }

    /**
     * Loads {@code keynest.default.v1} from the classpath policy document (schema-backed).
     */
    public static PasswordPolicy defaultPolicy() {
        return dev.keynest.policy.schema.PasswordPolicyLoader.loadDefault().toRuntimePolicy();
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

    public boolean forbidUsernameSubstring() {
        return forbidUsernameSubstring;
    }

    public Integer maxAgeDays() {
        return maxAgeDays;
    }

    public List<String> bannedSubstrings() {
        return bannedSubstrings;
    }

    public List<String> bannedRegexes() {
        return bannedRegexes;
    }

    /** Compiled forms of {@link #bannedRegexes()} for validation (same order). */
    public List<Pattern> bannedPatterns() {
        return bannedPatterns;
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
        private boolean forbidUsernameSubstring = true;
        private Integer maxAgeDays = 90;
        private List<String> bannedSubstrings = List.of();
        private List<String> bannedRegexes = List.of();
        private List<Pattern> bannedPatterns = List.of();

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

        public Builder forbidUsernameSubstring(boolean forbidUsernameSubstring) {
            this.forbidUsernameSubstring = forbidUsernameSubstring;
            return this;
        }

        public Builder maxAgeDays(Integer maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
            return this;
        }

        public Builder bannedSubstrings(List<String> bannedSubstrings) {
            this.bannedSubstrings = bannedSubstrings == null ? List.of() : List.copyOf(bannedSubstrings);
            return this;
        }

        public Builder bannedRegexes(List<String> bannedRegexes) {
            if (bannedRegexes == null || bannedRegexes.isEmpty()) {
                this.bannedRegexes = List.of();
                this.bannedPatterns = List.of();
                return this;
            }
            List<String> patterns = new ArrayList<>(bannedRegexes.size());
            List<Pattern> compiled = new ArrayList<>(bannedRegexes.size());
            for (String regex : bannedRegexes) {
                if (regex == null || regex.isBlank()) {
                    throw new IllegalArgumentException("bannedRegexes entries must be non-blank");
                }
                try {
                    compiled.add(Pattern.compile(regex));
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("invalid bannedRegex: " + regex, e);
                }
                patterns.add(regex);
            }
            this.bannedRegexes = List.copyOf(patterns);
            this.bannedPatterns = List.copyOf(compiled);
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
            if (maxAgeDays != null && maxAgeDays < 1) {
                throw new IllegalArgumentException("maxAgeDays must be >= 1 when set");
            }
            return new PasswordPolicy(this);
        }
    }
}
