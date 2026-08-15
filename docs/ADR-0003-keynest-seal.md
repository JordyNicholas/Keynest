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
