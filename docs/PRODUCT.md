# Keynest — Product Grooming

**Working product name:** Keynest  
**Repo rename target:** `fep-password` → `keynest`  
**Tagline:** Password tools you can embed — and a vault manager that uses them.

This document is the north star for product shape, stack, library boundaries, and MVP. It evolves the first grooming pass: Keynest is no longer “a Next.js vault app.” It is a **tools platform** plus a **manager product**.

---

## One-line vision

**Ship reusable password safety libraries** (encrypt/decrypt, validate against policy, generate, rotate) that any external service can adopt — and build **Keynest Manager** on top of those same libraries as the first-party vault product (self-host or cloud).

---

## Why rename

| Current | Problem |
|---|---|
| `fep-password` | Opaque; reads like coursework |
| “Password Generator” | Undersells vault + exportable tools |

**Keynest** = a nest for keys. Works for both the libraries (`keynest-*`) and the manager product.

> GitHub rename is manual: Settings → General → Repository name. Package/docs already use Keynest.

---

## Two offerings (same repo)

```text
┌─────────────────────────────────────────────────────────────┐
│                     Keynest platform                         │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ keynest-seal │  │ keynest-     │  │ keynest-policy    │  │
│  │ encrypt /    │  │ generate     │  │ validate / rules  │  │
│  │ decrypt /    │  │              │  │ strength / expiry │  │
│  │ verify env.  │  └──────────────┘  └───────────────────┘  │
│  └──────────────┘                                            │
│                         ▲ used by                            │
│         ┌───────────────┴────────────────┐                   │
│         │     Keynest Manager (product)   │                  │
│         │  Angular UI  +  Spring Boot API │                  │
│         │  vault CRUD, auth, notify, UX   │                  │
│         └────────────────────────────────┘                   │
│                         ▲ also used by                       │
│         ┌───────────────┴────────────────┐                   │
│         │   External services (your APIs) │                  │
│         │   Maven/npm clients of the libs │                  │
│         └────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

| Offering | What it is | Consumers |
|---|---|---|
| **Keynest Tools** | Versioned libraries + (optional later) HTTP sidecar | Any backend that needs password crypto/policy/generation |
| **Keynest Manager** | Full vault product (UI + API) | End users / teams; also the reference implementation of the tools |

The manager must call the **same** library APIs external services would. No private shortcuts. If Manager can do it, an integrator can do it.

---

## Problem we solve

### For integrators (tools)

1. **Seal** passwords/secrets for storage or transit with a clear, versioned envelope.
2. **Validate** candidate passwords against enforceable standards (and the same rules used to generate).
3. **Generate** passwords that always pass those standards.
4. **Update / rotate** with policy-aware helpers (new secret → re-seal → invalidate old metadata).

### For end users (Manager)

1. Store login data (URL, username/email, password).
2. Create / read / update / delete vault items.
3. Manage expiry (notify + guided update).
4. Unlock safely (master credential + optional MFA).
5. Deploy self-hosted or cloud.

---

## “New and unique” encryption — what that means here

You want differentiation. That is good. The unique part should be the **Keynest Seal toolkit** (API, envelope, key hierarchy, policy binding) — **not** a home-grown cipher or per-character hash scheme.

| Unique (yes — product IP) | Not unique / not safe |
|---|---|
| Versioned **Seal envelope** format (header + ciphertext + tag + key id) | Inventing a new block cipher |
| Opinionated **key hierarchy** (KEK/DEK, rotation, AAD bound to policy id) | Hashing each symbol + position |
| Single API: `seal` / `open` / `rewrap` / `verifyIntegrity` | “Reverse engineering” hashes |
| Same rules engine for **validate** and **generate** | Security through obscurity |
| Java library + optional HTTP tool service for polyglot callers | Storing recoverable secrets via hashing |

**Primitives stay standard and peer-reviewed:** Argon2id (KDF / password hashing), AES-256-GCM or XChaCha20-Poly1305 (authenticated encryption), CSPRNG for generation.

Your original instinct (“complex handling + challenge after validate”) maps cleanly to:

1. **Seal / Open** for vault secrets (encrypt/decrypt with integrity).
2. **Hash-and-compare** (whole master password) for authentication.
3. **MFA challenge** (email / SMS / TOTP) after auth succeeds.

See [ADR-0001](./ADR-0001-crypto-model.md) and [ADR-0003](./ADR-0003-keynest-seal.md).

---

## Tool surface (exportable)

These are the first-class libraries. Manager is a consumer.

### 1. `keynest-seal` — encrypt / decrypt / integrity

| Operation | Purpose |
|---|---|
| `seal(plaintext, keyRef, aad?)` | Produce a Keynest envelope (ciphertext + metadata) |
| `open(envelope, keyRef)` | Decrypt + verify auth tag / version |
| `rewrap(envelope, oldKey, newKey)` | Key rotation without changing plaintext |
| `fingerprint(envelope)` | Non-secret id for logs/audit |
| `hashPassword(password)` / `verifyPassword(password, hash)` | Auth path (Argon2id); separate from seal |

External auth services use `hashPassword` / `verifyPassword`.  
Vaults and secret stores use `seal` / `open`.

### 2. `keynest-policy` — standards enforcement

Declarative policy, shared by validate + generate:

```text
PasswordPolicy
  minLength, maxLength
  requireUpper, requireLower, requireDigit, requireSymbol
  minCategories
  forbidUsername / forbidEmailLocalPart
  forbidWhitespace
  excludeAmbiguous (0OIl1…)
  bannedList / regex denylist
  maxAgeDays (for rotation tooling)
```

| Operation | Purpose |
|---|---|
| `validate(password, policy, context?)` | Pass/fail + structured violations |
| `explain(policy)` | Human-readable rules for UI |
| `score(password)` optional | Strength hint (never a substitute for validate) |

### 3. `keynest-generate` — creation under the same rules

| Operation | Purpose |
|---|---|
| `generate(policy)` | Random password that **must** satisfy `validate` |
| `generateUntilValid(policy, maxAttempts)` | Internal retry with CSPRNG |
| `suggestRotation(oldEnvelopeMeta, policy)` | Helper for update flows |

**Invariant:** every generated password passes `keynest-policy` for the same policy id. No divergent “generator rules” vs “validator rules.”

### 4. Lifecycle helpers (can live in `keynest-policy` or a thin `keynest-lifecycle`)

| Operation | Purpose |
|---|---|
| `createSecret(policy)` | generate → (caller seals) |
| `updateSecret(policy)` | generate or accept candidate → validate → (caller seals) |
| `assertReadable / assertDeletable` | Authorization hooks left to Manager; libs stay pure |

Deletion is a **Manager/persistence** concern; libs do not delete rows. They can wipe byte arrays / clear sensitive buffers where the language allows.

---

## Manager product (first-party app)

**Keynest Manager** = Angular SPA + Spring Boot API + Postgres (+ notify worker).

Capabilities:

| Area | Behavior |
|---|---|
| Vault CRUD | create / read / update / delete credentials (URL, username/email, sealed password) |
| Tools UX | UI wrappers around generate + validate + seal (same libs) |
| Expiry | `expiresAt`, notify before, guided rotate using `generate` + `seal` |
| Auth | register/login via `hashPassword`/`verifyPassword`; optional MFA |
| Deploy | Docker Compose self-host; cloud profile (managed DB + mail/SMS providers) |

Manager API endpoints should be thin: validate input → call use case → use Keynest libs → map response. Domain logic does not depend on Spring Web types inside library modules.

---

## Target stack (decision)

Moving away from the current Next.js prototype.

| Layer | Choice | Rationale |
|---|---|---|
| Tools (libs) | **Java 21**, Maven multi-module | Export to JVM services; your preferred backend ecosystem |
| Manager API | **Spring Boot 3** | First-class consumer of the libs; clear module boundaries |
| Manager UI | **Angular** (current LTS) | Distinct UX surface; fits “Manager is a product” |
| DB | **PostgreSQL** | Vault metadata + envelopes |
| Jobs | Spring scheduler or queue worker | Expiry notifications |
| Crypto | JDK + proven libs (e.g. Bouncy Castle only if needed) | Argon2id + AES-GCM |
| Packing | Multi-module monorepo | `libs/*` + `apps/manager-api` + `apps/manager-web` |
| Deploy | Docker Compose + cloud compose/k8s profile | Same artifacts both ways |

### What happens to the existing Next.js code?

Treat it as a **prototype / archive**:

- Keep until Angular + Spring skeletons exist, then remove or move to `archive/next-prototype`.
- Do not invest further features in Next.js.
- Port generator UX ideas into Angular; replace `Math.random` with CSPRNG in the Java generator lib.

Optional later: TypeScript thin client that talks to a **Keynest Tools HTTP sidecar** for non-JVM stacks — still backed by the Java implementation so crypto stays one place.

---

## Repo layout (target)

```text
keynest/
  docs/                    PRODUCT, ADRs, threat model
  libs/
    keynest-seal/          encrypt, decrypt, password hash/verify
    keynest-policy/        standards + validate
    keynest-generate/      CSPRNG generation under policy
  apps/
    manager-api/           Spring Boot
    manager-web/           Angular
  deploy/
    docker-compose.yml
  archive/                 optional: old Next.js app
```

Publishing: Maven coordinates e.g. `dev.keynest:keynest-seal`, `keynest-policy`, `keynest-generate`.

---

## Service / module architecture

```text
 Angular (Manager Web)
        │  HTTPS
        ▼
 Spring Boot (Manager API)
   auth │ vault │ notify   ──▶  PostgreSQL
        │
        ├── keynest-seal
        ├── keynest-policy
        └── keynest-generate

 External Service (e.g. your other API)
        │
        └── same Maven deps (seal / policy / generate)
```

Logical services inside Manager API (packages, not necessarily separate deployables day one):

| Module | Responsibility |
|---|---|
| `auth` | Users, sessions/JWT, MFA orchestration |
| `vault` | Item CRUD, stores Seal envelopes |
| `notify` | Expiry scan, email/SMS adapters |
| `tools` | Optional HTTP façade exposing validate/generate/seal for trusted internal use |

**Day-one recommendation:** modular Spring Boot monolith depending on the three libs. Split Tools into a standalone HTTP service only when non-JVM or multi-language consumers need it.

---

## Domain model (Manager MVP)

```text
User
  id, email, passwordHash, kdfParams, mfaEnabled, createdAt

VaultItem
  id, userId, label, url, username,
  envelope (Keynest Seal blob),   // sealed password
  policyId,                       // which standard applied
  expiresAt, lastRotatedAt, notifyBeforeDays,
  createdAt, updatedAt

PasswordPolicyEntity          // optional persisted policies
  id, ownerId|system, name, rulesJson, version

MfaChallenge
  id, userId, channel, codeHash, expiresAt, consumedAt

NotificationOutbox
  id, userId, vaultItemId, channel, payload, status, scheduledAt
```

---

## Security requirements (non-negotiable)

- No plaintext vault secrets at rest.
- No novel ciphers; uniqueness is envelope + API + policy engine.
- Constant-time compares for hashes and OTPs.
- Rate-limit auth and MFA.
- Libs avoid logging secrets; APIs never log envelopes’ plaintext.
- Clear sensitive `char[]`/`byte[]` where practical.
- Threat model before public release (`docs/THREAT_MODEL.md`).

---

## MVP slice (revised for platform + new stack)

1. **Grooming docs** — platform split, stack, Seal concept.  
2. **Maven multi-module skeleton** — `seal` / `policy` / `generate` + Spring Boot tools API + Angular shell. **(landed)**  
3. Harden libs (golden Seal vectors, richer policies, rewrap).  
4. Manager API vault + auth persistence (Postgres).  
5. Manager Web vault CRUD UI.  
6. Notify — email on approaching expiry.  
7. Compose full stack + cloud notes.  
8. Publish libs / sample external consumer module.

---

## Non-goals (for now)

- Browser extension / autofill.
- Org sharing / RBAC.
- Breach monitoring.
- Replacing Bitwarden feature-for-feature.
- Custom cryptographic primitives.
- SMS as the only MFA factor (optional channel only).

---

## Open decisions

1. **Envelope details (Seal v1)** — byte layout, version byte, KID, AAD fields. Draft in ADR-0003; finalize before coding seal.
2. **Zero-knowledge vs server-side open** — still recommend server-side `open` for Manager MVP; envelope designed so a future Angular ZK mode can open client-side with WebCrypto or WASM.
3. **Lib distribution** — GitHub Packages / Maven Central later vs path dependencies first. Recommend path/reactor modules until APIs stabilize.
4. **Tools HTTP sidecar** — defer until a non-JVM consumer appears.
5. **Master recovery** — recovery key download **or** reset-wipes-vault; must be explicit in Manager UX.
6. **Policy defaults** — ship system policy `keynest.default.v1` as a JSON document validated by `password-policy.schema.json` (landed; see ADR-0004).

---

## Success criteria

- External sample service depends on `keynest-seal` + `keynest-policy` + `keynest-generate` and can seal/validate/generate without Manager.
- Manager create/read/update/delete vault items using those libs only for crypto/policy/generation.
- Generated passwords always validate against the same policy.
- Self-host Compose path documented and runnable.
- README states platform + manager clearly in a short read.

---

## Rename checklist

- [x] Product/docs identity → Keynest  
- [x] Introduce Maven artifact names under `dev.keynest`  
- [x] Retire Next.js prototype into `archive/`  
- [ ] GitHub repository rename → `keynest` (manual)
