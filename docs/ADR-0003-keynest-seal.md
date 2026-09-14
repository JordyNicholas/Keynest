# ADR 0003 — Keynest Seal (unique toolkit, standard primitives)

## Status

Accepted (product grooming)

## Context

We want a distinctive, exportable encryption/decryption tool for external services and for Keynest Manager. An earlier idea proposed per-symbol hashing and reverse-engineering for validation. That is cryptographically weak and conflates **vault secrecy** (needs encryption) with **authentication** (needs one-way hashing) and **MFA** (needs a second factor).

Differentiation still matters: integrators should feel they adopted a **Keynest** way of handling passwords, not a loose bag of AES calls.

## Decision

### Unique layer (our IP)

Define **Keynest Seal** as a versioned envelope + API:

- `seal` / `open` / `rewrap` / `fingerprint`
- Header fields: `version`, `algorithmId`, `keyId`, `nonce`, `authTag`, optional `policyId` in AAD
- Documented binary or base64url encoding so any official client is interoperable
- Key hierarchy helpers (derive DEK from KEK, rotate via `rewrap`)

### Standard layer (not reinvented)

- Authenticated encryption: **AES-256-GCM** (initial algorithm id `1`)
- Password-based key derivation / password hashing: **Argon2id**
- Randomness: SecureRandom / CSPRNG only

### Explicitly rejected

- Per-character or per-position hashing for storage or auth
- Any claim of decrypting a one-way hash
- Unpublished custom ciphers or “secret” algorithms

### Auth vs vault

| Need | Seal API |
|---|---|
| Store recoverable secret | `seal` / `open` |
| Verify login password | `hashPassword` / `verifyPassword` |
| Step-up challenge | Manager Auth + MFA (not part of Seal crypto) |

## Consequences

- Marketing “unique encryption tool” means **unique envelope and workflow**, auditable against known primitives.
- External services get a stable Maven artifact and clearer compliance story.
- Future algorithm agility via `version` / `algorithmId` without breaking the API names.
- First envelope layout (`Seal v1`) must be locked with tests and golden vectors before Manager stores production data.

## Appendix — `rewrap` and AAD / `policyId` binding

`rewrap(envelope, oldKey, newKey, newKeyId, aad?)` rotates the wrapping key without changing plaintext:

1. `open(envelope, oldKey, aad)` — fails on wrong key or AAD mismatch (same as normal open).
2. `seal(plaintext, newKey, newKeyId, aad)` — new nonce; same Seal v1 layout; caller supplies the new key id.

`policyId` (and any other binding context) lives in **AAD**, not in the envelope header. Rewrap uses the **same** AAD for open and seal; it does not migrate or clear AAD. Callers that sealed with `policy:<id>` must rewrap with that same AAD.

## Appendix — Sensitive buffer hygiene

`keynest-seal` clears intermediate `byte[]` / `char[]` where practical:

- After `seal`: local nonce, keyId bytes, `ciphertext||tag` scratch, and split ciphertext/tag arrays (envelope keeps its own copies).
- After `open`: local ciphertext/tag copies and the combined sealed buffer (returned plaintext is caller-owned and not cleared).
- After `rewrap`: recovered plaintext wiped after reseal.
- After `fingerprint`: raw envelope bytes and hash scratch wiped after encoding.
- `PasswordHasher` already wipes password `char[]` via Argon2 `wipeArray`.

**JVM limits:** `Arrays.fill` / `wipeArray` do not guarantee absence from heap dumps, GC survivors, `SecretKeySpec` internal copies, or JIT/stack copies. Hygiene reduces casual exposure; it is not a formal confidentiality boundary. Do not break public APIs for stronger theatre.
