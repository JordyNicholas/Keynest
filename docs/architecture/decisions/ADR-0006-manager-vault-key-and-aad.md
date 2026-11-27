# ADR 0006 — Manager vault key configuration and item binding

## Status

Accepted in maintainer-approved PR #68 on November 11, 2026.

## Context

Manager currently encrypts every vault password under one server-supplied AES-256 key. The local and Docker configurations both default to a public demonstration key, and envelope AAD contains only `policyId`. A deployed process can therefore start with known key material, while an envelope copied between items with the same policy is not cryptographically bound to its owner or item identifier.

This ADR hardens the current MVP key model without claiming that a single server key is the final custody architecture. Per-user DEKs wrapped by a KEK and external KMS/Vault adapters require a separate migration design.

## Decision

1. Vault key configuration is validated during application startup. The key must be non-empty, valid hexadecimal and exactly 32 bytes; key ID must be non-empty.
2. The documented demo key requires the explicit `keynest.vault.allow-demo-key=true` development opt-in. Profiles `prod` and `production` reject it even if that opt-in is set.
3. The `docker` profile has no vault-key default and disallows the demo key by default. Local Compose opts in visibly so it remains a reproducible demonstration. A deployed environment supplies `KEYNEST_VAULT_KEY_HEX`, keeps `KEYNEST_ALLOW_DEMO_VAULT_KEY` unset/false and activates `prod` alongside its database profile.
4. New or password-updated vault items use AAD version 1, encoded as a domain separator, version, owner UUID, item UUID and length-prefixed UTF-8 policy ID. UUIDs use their two 64-bit values in network byte order.
5. `aad_version` records the binding contract. Null identifies legacy envelopes whose AAD was only UTF-8 `policyId`; these remain readable. Updating their password reseals them under version 1. Unknown versions fail closed.

The AAD version is authenticated indirectly: changing a v1 record to the legacy/null value produces different AAD and authentication fails. Legacy records retain their original substitution risk until resealed.

## Consequences

- Missing, malformed and known-demo production keys stop startup instead of serving requests.
- New envelopes cannot be moved to another owner, item or policy without failing AES-GCM authentication.
- Existing development databases remain readable. There is no bulk migration in this change; a later key-custody migration must reseal legacy records before claiming universal item binding.
- Changing the configured vault key still makes existing envelopes unreadable. Operators must preserve and back up key material; managed rotation is future work.
- This remains a server-side-open model. It does not provide zero knowledge, per-user cryptographic isolation or protection from a compromised API process.

## Verification

- Validator tests cover missing/short/demo keys, explicit development use, and production-profile rejection.
- AAD tests cover determinism, field separation, successful opening with the matching binding and authentication failure after item substitution.
- Existing Manager integration tests cover creation and reveal with the new AAD version.
