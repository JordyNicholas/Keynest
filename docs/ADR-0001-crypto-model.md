# ADR 0001 — Vault encryption and master-password hashing

## Status

Accepted (product grooming)

## Context

Early design proposed splitting a password into symbols, hashing each position, then “reverse engineering” on validation, followed by an email/SMS random-string challenge.

We need both:

1. Recoverable site passwords (vault).
2. Verifiable master credentials (login), optionally with MFA.

## Decision

1. **Vault fields** use authenticated encryption (AES-256-GCM or XChaCha20-Poly1305). Key material comes from Argon2id (or equivalent) over the master password + salt.
2. **Master password verification** uses Argon2id over the entire password; verify by re-hash + constant-time compare. Never attempt to invert a hash.
3. **MFA** (email/SMS OTP or TOTP) runs *after* successful master-password verification as a separate Auth step.
4. **Reject** per-character / per-position hashing for any secret storage or verification.

## Consequences

- Crypto is boring and standard — easier to audit and explain.
- Self-host MVP can decrypt server-side; ciphertext format should allow a later zero-knowledge client.
- MFA delivery providers become adapters behind Notify/Auth, not part of the hash scheme.
- Existing `bcrypt` dependency may be replaced or limited to transitional use; prefer Argon2id for new master-password hashing.
