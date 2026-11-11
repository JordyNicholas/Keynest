# Keynest — Threat model (stub)

**Status:** Stub (E0 / #15)  
**Expand before public release:** E6 / #43  
**Companions:** [PRODUCT.md](./PRODUCT.md) security non-negotiables, [ADR-0001](./ADR-0001-crypto-model.md), [ADR-0003](./ADR-0003-keynest-seal.md), [ROADMAP.md](./ROADMAP.md) Phase 0 / 6.

This is a living checklist, not a full STRIDE report. It exists so seal/open, auth, and key handling stay explicitly in scope while features land. Expand mitigations and residual risk under the E6 release pass.

---

## In scope

| Surface | Why |
| --- | --- |
| **Seal / open / rewrap** (`keynest-seal`) | Recoverable vault secrets; envelope integrity |
| **Auth** (register / login / session) | Master-password hashing; account takeover |
| **Tools API** (validate / generate / demo seal-open) | Abuse as an oracle; demo-key leakage |
| **Vault persistence** (envelopes at rest) | DB compromise must not yield plaintext |
| **Logging / metrics** | Scraping secrets from logs |

Out of scope for this stub (tracked elsewhere): MFA (#45), recovery key (#46), ZK client-side open (#48), org RBAC, breach monitoring.

---

## Assets

| Asset | Sensitivity | Notes |
| --- | --- | --- |
| Master password (user) | Critical | Verified via Argon2id (`hashPassword` / `verifyPassword`); never recoverable from hash |
| Vault item plaintext (site passwords) | Critical | Only after authenticated `open`; never stored plaintext |
| Seal envelope (ciphertext + tag + metadata) | High | Safe at rest only if DEK/KEK stays secret |
| DEK / KEK / tools demo key | Critical | Server-held for MVP; must come from env/config in prod — no fixed hex |
| Session bearer / stored digest | High | Raw bearer grants vault access; database stores only SHA-256 digest after #71 |
| Policy id / AAD bindings | Medium | Integrity / binding; not secret by themselves |
| Notify outbox payloads | Medium | Must not include plaintext passwords |

---

## Trust boundaries

```text
[ Browser / Angular SPA ]
        │  HTTPS (assume TLS in deploy)
        ▼
[ Manager API — Spring Boot ]
   auth │ vault │ tools │ notify
        │
        ├── keynest-seal / policy / generate  (in-process libs)
        ▼
[ PostgreSQL ]   envelopes + user hashes only
```

| Boundary | Trust assumption | Failure mode if broken |
| --- | --- | --- |
| Browser ↔ API | TLS; CORS locked to known origins in prod | Token theft, MITM, XSS → session abuse |
| API process | Operator controls host; libs are not a separate trust zone | Memory dump / process compromise → DEK + open path |
| API ↔ DB | DB credentials private; no public DB | Envelope theft; still needs key to decrypt |
| Tools demo seal/open | Dev-only or gated | Fixed demo DEK decrypts any tools-demo envelope |
| External consumer of libs | Caller supplies keys; Keynest does not hold theirs | Integrator key mismanagement (their threat model) |

MVP crypto stance (accepted): **server-side `open`**. Envelope format must remain client-openable later without a format break (see ADR-0003).

---

## Abuse cases (initial)

| ID | Abuse | Impact | Current / planned control |
| --- | --- | --- | --- |
| A1 | Brute-force login or `/tools/validate`; spoofed forwarding headers bypass per-client limits | Account takeover; password oracle | In-memory rate limits on validate/register/login (#17); direct peer by default, explicit trusted-proxy CIDRs and bounded identity map (#75); Argon2id cost. Counters remain per JVM; shared edge/distributed enforcement is required across replicas |
| A2 | Production ships fixed tools/vault hex DEK | Anyone who knows the default opens envelopes | Tools demo seal/open gated off in `docker` / no demo hex (#16); #30 rejects missing/demo vault keys outside explicit local development |
| A3 | Log scraping (request bodies, exceptions, fingerprints misused) | Plaintext or near-secret leakage | Never log passwords, envelopes’ plaintext, or raw keys; review in #43 |
| A4 | Unauthenticated tools seal/open in prod | Free encrypt/decrypt with known key | Demo seal/open disabled by default on `docker` profile (#16); generate/validate remain available |
| A5 | Stolen DB dump | Offline attack on envelopes | AES-GCM envelopes; key not in DB; rotate via `rewrap` (#20) |
| A6 | XSS / malicious script in Manager Web | Session + reveal API abuse | POST-only reveal responses are `no-store`; UI auto-conceals after 15 seconds and clears on destruction (#73). JavaScript/XSS can still read a displayed secret; CSP and dependency hardening remain necessary |
| A7 | Confused deputy: vault open without authz | Cross-user secret read | Authn + user-scoped vault queries (landed with #27/#28); keep tests green |
| A8 | Policy bypass (generate vs validate diverge) | Weak passwords accepted | Single policy engine; generate↔validate invariant (E1) |
| A9 | Database or browser storage exposes session bearers | Account/vault access until expiry | Persist only token digests; browser uses tab-scoped sessionStorage and clears legacy localStorage (#71). XSS remains a bearer-theft risk |

---

## Explicit non-claims

- Keynest does **not** invent a cipher. Uniqueness is the **Seal envelope + API + policy binding**, not a novel primitive ([ADR-0001](./ADR-0001-crypto-model.md), [ADR-0003](./ADR-0003-keynest-seal.md)).
- Hashing is one-way. There is no “reverse engineering” of password hashes.
- This stub is **not** a penetration-test report or compliance attestation.

---

## Open gaps tracked on the board

| Gap | Issue |
| --- | --- |
| Threat model stub (this file) | #15 *(landed)* |
| Gate tools demo key | #16 *(landed — config gate; local demo documented in README)* |
| Rate-limit validate + auth | #17 *(landed — filter + `ApiPaths.RATE_LIMITED_POSTS`; self-host knobs in README)* |
| `rewrap` for key rotation | #20 |
| Buffer hygiene | #22 |
| Prod vault key from env + owner/item/policy AAD | #30 *(landed in approved PR #68; legacy envelopes remain policy-only until resealed)* |
| Release expansion + logging review | #43 |

When #43 lands, expand each abuse row with residual risk, owner, and verify-in-prod checks.
