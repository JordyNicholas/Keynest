package dev.keynest.manager.auth;

import dev.keynest.seal.PasswordHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private static final int SESSION_DAYS = 7;

    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserAccountRepository users,
            AuthSessionRepository sessions,
            PasswordHasher passwordHasher
    ) {
        this.users = users;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public AuthResponse register(String email, String password) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isEmpty() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password required");
        }
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        String hash = passwordHasher.hashPassword(password.toCharArray());
        UserAccount user = new UserAccount(UUID.randomUUID(), normalized, hash, Instant.now());
        users.save(user);
        return issueSession(user);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        UserAccount user = users.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        if (!passwordHasher.verifyPassword(password.toCharArray(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        AuthSession session = sessions.findByToken(token)
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
        sessions.deleteByToken(extractToken(authorizationHeader));
    }

    private AuthResponse issueSession(UserAccount user) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = Instant.now();
        AuthSession session = new AuthSession(
                UUID.randomUUID(),
                user.getId(),
                token,
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
