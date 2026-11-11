package dev.keynest.manager.ai;

/** Provider-neutral boundary. Credentials are transport-only, never prompt data. */
public interface AiProvider {
  String id();
  String explain(PolicyFacts facts, ProviderCredential credential);
}
