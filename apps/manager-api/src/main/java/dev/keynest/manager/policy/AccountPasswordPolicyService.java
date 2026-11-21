package dev.keynest.manager.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.auth.UserAccountRepository;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.schema.PolicyRegistry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AccountPasswordPolicyService {
  private final UserAccountRepository users;
  private final PolicyRegistry registry;
  private final ObjectMapper json = new ObjectMapper();

  public AccountPasswordPolicyService(UserAccountRepository users, PolicyRegistry registry) {
    this.users = users;
    this.registry = registry;
  }

  @Transactional
  public PasswordPolicy effective(UserAccount user) {
    if (user == null) return registry.runtime(PasswordPolicy.DEFAULT_POLICY_ID);
    UserAccount current = requireLocked(user);
    return decode(current);
  }

  /** Metadata inspection does not acquire the policy mutation lock. */
  @Transactional(readOnly = true)
  public View inspect(UserAccount user) {
    UserAccount current = users.findById(user.getId())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid session"));
    return view(decode(current));
  }

  private PasswordPolicy decode(UserAccount current) {
    if (current.getPasswordPolicy() == null) return registry.runtime(PasswordPolicy.DEFAULT_POLICY_ID);
    try {
      Stored stored = json.readValue(current.getPasswordPolicy(), Stored.class);
      return stored.rules().runtime(stored.id());
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Stored account policy could not be read");
    }
  }

  @Transactional
  public View update(UserAccount user, Settings settings) {
    UserAccount current = requireLocked(user);
    if ("default".equals(settings.mode())) {
      current.setPasswordPolicy(null);
    } else if ("custom".equals(settings.mode()) && settings.rules() != null) {
      Rules rules = settings.rules();
      rules.runtime("custom-check");
      try {
        current.setPasswordPolicy(json.writeValueAsString(
          new Stored("keynest.account." + user.getId() + "." + UUID.randomUUID(), rules)));
      } catch (JsonProcessingException failure) {
        throw new IllegalStateException("Account policy could not be saved");
      }
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose default or custom password rules");
    }
    users.saveAndFlush(current);
    return view(effective(current));
  }

  public View view(PasswordPolicy policy) {
    return new View(policy.id(), Rules.from(policy));
  }

  private UserAccount requireLocked(UserAccount user) {
    return users.findForPolicyUpdate(user.getId())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid session"));
  }

  public record View(String id, Rules rules) {}
  public record Stored(String id, Rules rules) {}
  public record Settings(@NotNull String mode, @jakarta.validation.Valid Rules rules) {}
  public record Rules(
    @Min(8) @Max(128) int minLength,
    @Min(8) @Max(128) int maxLength,
    boolean requireUpper, boolean requireLower, boolean requireDigit, boolean requireSymbol,
    @Min(0) @Max(4) int minCategories,
    boolean excludeAmbiguous, boolean forbidWhitespace, boolean forbidUsernameSubstring,
    @NotNull @Size(max = 20) List<@NotNull @Size(min = 1, max = 64) String> bannedSubstrings,
    @NotNull @Size(max = 1) List<@NotNull @Size(max = 32) String> bannedRegexes,
    @Min(1) @Max(36500) Integer maxAgeDays
  ) {
    PasswordPolicy runtime(String id) {
      if (minLength > maxLength || bannedRegexes.stream().anyMatch(regex -> !"(.)\\1{3,}".equals(regex))) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password policy settings");
      }
      return PasswordPolicy.builder().id(id).minLength(minLength).maxLength(maxLength)
        .requireUpper(requireUpper).requireLower(requireLower).requireDigit(requireDigit).requireSymbol(requireSymbol)
        .minCategories(minCategories).excludeAmbiguous(excludeAmbiguous).forbidWhitespace(forbidWhitespace)
        .forbidUsernameSubstring(forbidUsernameSubstring).bannedSubstrings(bannedSubstrings)
        .bannedRegexes(bannedRegexes).maxAgeDays(maxAgeDays).build();
    }

    static Rules from(PasswordPolicy policy) {
      return new Rules(policy.minLength(), policy.maxLength(), policy.requireUpper(), policy.requireLower(),
        policy.requireDigit(), policy.requireSymbol(), policy.minCategories(), policy.excludeAmbiguous(),
        policy.forbidWhitespace(), policy.forbidUsernameSubstring(), policy.bannedSubstrings(), policy.bannedRegexes(), policy.maxAgeDays());
    }
  }
}
