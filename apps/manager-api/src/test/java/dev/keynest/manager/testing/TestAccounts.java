package dev.keynest.manager.testing;

import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.auth.UserAccountRepository;
import dev.keynest.manager.security.PasswordHashingService;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Test-classpath-only database setup. Never packaged as an endpoint or production bypass. */
@Component
public class TestAccounts {
  private final UserAccountRepository users;
  private final PasswordHashingService passwords;
  private final AuthService auth;

  public TestAccounts(UserAccountRepository users, PasswordHashingService passwords, AuthService auth) {
    this.users = users;
    this.passwords = passwords;
    this.auth = auth;
  }

  public AuthService.AuthResponse register(String email, String password) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    users.saveAndFlush(account(UUID.randomUUID(), normalized, passwords.hash(password), Instant.now()));
    return auth.login(normalized, password);
  }

  public static UserAccount account(UUID id, String email, String hash, Instant now) {
    return UserAccount.verified(id, email, email, hash, now);
  }
}
