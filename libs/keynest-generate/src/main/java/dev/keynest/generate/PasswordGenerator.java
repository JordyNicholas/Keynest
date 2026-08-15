package dev.keynest.generate;

import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PasswordValidator;
import dev.keynest.policy.ValidationResult;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Generates passwords that must satisfy the same {@link PasswordPolicy} used for validation.
 */
public final class PasswordGenerator {

    private static final int DEFAULT_MAX_ATTEMPTS = 10_000;

    private final SecureRandom random;
    private final int maxAttempts;

    public PasswordGenerator() {
        this(new SecureRandom(), DEFAULT_MAX_ATTEMPTS);
    }

    public PasswordGenerator(SecureRandom random, int maxAttempts) {
        this.random = Objects.requireNonNull(random, "random");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public String generate(PasswordPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        String charset = PasswordValidator.charsetFor(policy);
        if (charset.isEmpty()) {
            throw new IllegalStateException("policy produced empty charset");
        }

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = randomString(policy.minLength(), charset);
            // Ensure required categories by injecting one of each when required
            candidate = ensureCategories(candidate, policy, charset);
            ValidationResult result = PasswordValidator.validate(candidate, policy);
            if (result.valid()) {
                return candidate;
            }
        }
        throw new IllegalStateException("failed to generate a policy-compliant password after " + maxAttempts + " attempts");
    }

    private String randomString(int length, String charset) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }
        return sb.toString();
    }

    private String ensureCategories(String candidate, PasswordPolicy policy, String charset) {
        char[] chars = candidate.toCharArray();
        int idx = 0;
        if (policy.requireLower()) {
            chars[idx++ % chars.length] = pick(PasswordValidator.LOWER);
        }
        if (policy.requireUpper()) {
            chars[idx++ % chars.length] = pick(PasswordValidator.UPPER);
        }
        if (policy.requireDigit()) {
            chars[idx++ % chars.length] = pick(PasswordValidator.DIGITS);
        }
        if (policy.requireSymbol()) {
            chars[idx++ % chars.length] = pick(PasswordValidator.SYMBOLS);
        }
        // Shuffle lightly with remaining charset draws for positions already set is enough for MVP
        for (int i = 0; i < chars.length; i++) {
            if (charset.indexOf(chars[i]) < 0) {
                chars[i] = charset.charAt(random.nextInt(charset.length()));
            }
        }
        return new String(chars);
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
