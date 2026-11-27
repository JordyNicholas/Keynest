package dev.keynest.manager.auth;

import dev.keynest.manager.security.PasswordHashingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final int SESSION_DAYS = 7;
    private static final int MIN_MASTER_PASSWORD_LENGTH = 15;

    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordHashingService passwords;
    private final String missingAccountHash;
    private final LoginAttemptLimiter loginAttempts;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserAccountRepository users,
            AuthSessionRepository sessions,
            PasswordHashingService passwords,
            LoginAttemptLimiter loginAttempts
    ) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.loginAttempts = loginAttempts;
        // Non-account work target, prepared once before serving requests. Its
        // input is not a credential; a missing account can never issue a session.
        this.missingAccountHash = passwords.hash("keynest missing-account verification");
    }

    @Transactional
    public AuthResponse register(String email, String password) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password required");
        }
        if (normalized.length() > UserAccount.EMAIL_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is too long");
        }
        if (password.codePointCount(0, password.length()) < MIN_MASTER_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "master password must be at least " + MIN_MASTER_PASSWORD_LENGTH + " characters");
        }
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        String hash = passwords.hash(password);
        UserAccount user = new UserAccount(UUID.randomUUID(), normalized, hash, Instant.now());
        users.save(user);
        return issueSession(user);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty() || normalized.length() > UserAccount.EMAIL_MAX_LENGTH || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        try (var attempt = loginAttempts.begin(normalized)) {
            UserAccount user = users.findByEmailIgnoreCase(normalized).orElse(null);
            // Admitted known/unknown attempts still share the same password-work budget.
            boolean verified = passwords.verify(password, user == null ? missingAccountHash : user.getPasswordHash());
            if (user == null || !verified) {
                attempt.failed();
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
            }
            AuthResponse response = issueSession(user);
            attempt.succeeded();
            return response;
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        AuthSession session = sessions.findByTokenDigest(SessionTokenDigest.digest(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid session"));
        if (session.isExpired(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session expired");
        }
        return users.findById(session.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user missing"));
    }

    @Transactional
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return;
        }
        sessions.deleteByTokenDigest(SessionTokenDigest.digest(extractToken(authorizationHeader)));
    }

    private AuthResponse issueSession(UserAccount user) {
        byte[] raw = new byte[32];
        String token;
        try {
            random.nextBytes(raw);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
        Instant now = Instant.now();
        AuthSession session = new AuthSession(
                UUID.randomUUID(),
                user.getId(),
                SessionTokenDigest.digest(token),
                now.plus(SESSION_DAYS, ChronoUnit.DAYS),
                now
        );
        sessions.save(session);
        return new AuthResponse(user.getId(), user.getEmail(), token, session.getExpiresAt());
    }

    private static String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        if (value.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        return value;
    }

    public record AuthResponse(UUID userId, String email, String token, Instant expiresAt) {
    }
}
