package dev.keynest.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Validates passwords against a {@link PasswordPolicy}. Same rules drive generation.
 */
public final class PasswordValidator {

    public static final String AMBIGUOUS = "0OIl1";
    public static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    public static final String DIGITS = "23456789";
    public static final String SYMBOLS = "!@#$%^&*()-_=+[]{}?,.";

    public static final String UPPER_ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LOWER_ALL = "abcdefghijklmnopqrstuvwxyz";
    public static final String DIGITS_ALL = "0123456789";

    private PasswordValidator() {
    }

    public static ValidationResult validate(String password, PasswordPolicy policy) {
        return validate(password, policy, null);
    }

    public static ValidationResult validate(String password, PasswordPolicy policy, String usernameOrEmail) {
        Objects.requireNonNull(policy, "policy");
        List<String> violations = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            return ValidationResult.failed(List.of("password.required"));
        }

        if (password.length() < policy.minLength()) {
            violations.add("password.too_short");
        }
        if (password.length() > policy.maxLength()) {
            violations.add("password.too_long");
        }
        if (policy.forbidWhitespace() && password.chars().anyMatch(Character::isWhitespace)) {
            violations.add("password.whitespace_forbidden");
        }
        if (policy.excludeAmbiguous() && password.chars().anyMatch(c -> AMBIGUOUS.indexOf(c) >= 0)) {
            violations.add("password.ambiguous_chars");
        }

        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));

        if (policy.requireUpper() && !hasUpper) {
            violations.add("password.require_upper");
        }
        if (policy.requireLower() && !hasLower) {
            violations.add("password.require_lower");
        }
        if (policy.requireDigit() && !hasDigit) {
            violations.add("password.require_digit");
        }
        if (policy.requireSymbol() && !hasSymbol) {
            violations.add("password.require_symbol");
        }

        int categories = 0;
        if (hasUpper) {
            categories++;
        }
        if (hasLower) {
            categories++;
        }
        if (hasDigit) {
            categories++;
        }
        if (hasSymbol) {
            categories++;
        }
        if (categories < policy.minCategories()) {
            violations.add("password.min_categories");
        }

        if (policy.forbidUsernameSubstring() && usernameOrEmail != null && !usernameOrEmail.isBlank()) {
            String local = usernameOrEmail.contains("@")
                    ? usernameOrEmail.substring(0, usernameOrEmail.indexOf('@'))
                    : usernameOrEmail;
            String needle = local.toLowerCase(Locale.ROOT);
            if (needle.length() >= 3 && password.toLowerCase(Locale.ROOT).contains(needle)) {
                violations.add("password.contains_username");
            }
        }

        String lowerPassword = password.toLowerCase(Locale.ROOT);
        for (String banned : policy.bannedSubstrings()) {
            if (banned != null && !banned.isBlank() && lowerPassword.contains(banned.toLowerCase(Locale.ROOT))) {
                violations.add("password.banned_substring");
                break;
            }
        }

        for (var pattern : policy.bannedPatterns()) {
            if (pattern.matcher(password).find()) {
                violations.add("password.banned_regex");
                break;
            }
        }

        return violations.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(violations);
    }

    public static String charsetFor(PasswordPolicy policy) {
        StringBuilder sb = new StringBuilder();
        boolean ambiguous = policy.excludeAmbiguous();
        if (policy.requireLower() || policy.minCategories() > 0) {
            sb.append(ambiguous ? LOWER : LOWER_ALL);
        }
        if (policy.requireUpper() || policy.minCategories() > 0) {
            sb.append(ambiguous ? UPPER : UPPER_ALL);
        }
        if (policy.requireDigit() || policy.minCategories() > 0) {
            sb.append(ambiguous ? DIGITS : DIGITS_ALL);
        }
        if (policy.requireSymbol() || policy.minCategories() > 0) {
            sb.append(SYMBOLS);
        }
        if (sb.isEmpty()) {
            sb.append(ambiguous ? LOWER : LOWER_ALL);
        }
        return sb.toString();
    }
}
