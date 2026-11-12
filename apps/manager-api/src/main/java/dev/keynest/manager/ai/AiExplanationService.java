package dev.keynest.manager.ai;

import dev.keynest.manager.config.AiProperties;
import dev.keynest.policy.PasswordPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiExplanationService {
  private final AiProperties properties;
  private final Map<String, AiProvider> providers;
  private final PasswordPolicy policy;
  private final AiAdmission admission;

  public AiExplanationService(AiProperties properties, List<AiProvider> providers, PasswordPolicy policy, AiAdmission admission) {
    this.properties = properties;
    this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(AiProvider::id, Function.identity()));
    this.policy = policy;
    this.admission = admission;
  }

  public void requireEnabled() {
    if (!properties.isEnabled()) throw new AiFailure(AiFailure.Code.AI_DISABLED);
  }

  public Explanation explain(UUID user, String providerId, String policyId, String suppliedKey) {
    requireEnabled();
    AiProvider provider = providers.get(providerId);
    if (provider == null) throw new AiFailure(AiFailure.Code.AI_PROVIDER_UNSUPPORTED);
    if (!PasswordPolicy.DEFAULT_POLICY_ID.equals(policyId)) throw new AiFailure(AiFailure.Code.AI_POLICY_UNSUPPORTED);
    try (var credential = new ProviderCredential(suppliedKey); var lease = admission.acquire(user)) {
      String text = provider.explain(PolicyFacts.from(policy), credential);
      if (text == null || text.isBlank() || text.length() > 4000 || credential.appearsIn(text)) {
        throw new AiFailure(AiFailure.Code.AI_INVALID_OUTPUT);
      }
      return new Explanation(providerId, policyId, text);
    } catch (AiFailure failure) {
      throw failure;
    } catch (RuntimeException ignored) {
      throw new AiFailure(AiFailure.Code.AI_UNAVAILABLE);
    }
  }

  public record Explanation(String providerId, String policyId, String explanation) {}
}
