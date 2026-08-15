# Keynest — Product Grooming

**Working product name:** Keynest  
**Repo rename target:** `fep-password` → `keynest`  
**Tagline:** Self-hostable password vault with cloud option, expiry alerts, and strong generation.

This document replaces the early “password generator portfolio app” framing with a clear product definition, security model, service split, and MVP scope.

---

## Why rename

| Current | Problem |
|---|---|
| `fep-password` | Opaque acronym; reads like a homework repo |
| “Password Generator” title | Undersells the real product (vault + lifecycle + auth) |

**Keynest** = a nest for your keys. Short, pronounceable, available as a package name, works for self-host *and* SaaS.

Alternatives if you dislike it: `coffer`, `lockhaven`, `passkeep`, `vaultforge`.

> GitHub rename is a Settings → General → Repository name change on `JordyNicholas/fep-password`. This PR only renames the package and docs; you (or a maintainer) must rename the remote repo.

---

## Problem we solve

People need a place to:

1. **Store** site credentials (URL, username/email, secret).
2. **Generate** strong passwords when creating or rotating credentials.
3. **Know when to rotate** (expiry / policy), with notify + guided update.
4. **Unlock the vault safely** (master credential + optional second factor).
5. **Deploy either way** — Docker/self-host *or* a hosted cloud tier.

---

## Critical correction: hashing vs encryption

The original idea (split password into symbols → hash each position → “reverse engineer” on login → then email/SMS challenge) must not ship. It fails for two different jobs at once.

### Job A — Vault secrets (site passwords you must *read back*)

You need **encryption**, not hashing.

| Approach | Can recover password for autofill? | Safe? |
|---|---|---|
| Hash whole password | No | Wrong tool |
| Hash each character + position | No (and trivial to crack) | **Dangerous** |
| Encrypt with key derived from master password | Yes | Correct |

Per-character hashing is ~95 guesses per symbol. An attacker recovers the full password in seconds. Position maps also leak length and structure.

**Correct vault model (industry standard):**

1. User enters **master password** (never stored in plaintext).
2. Derive a vault key with a memory-hard KDF: **Argon2id** (or scrypt), with random salt.
3. Encrypt each vault item with **AES-256-GCM** (or XChaCha20-Poly1305).
4. Store ciphertext + nonce + salt + auth tag. Server (or DB) never sees plaintext site passwords when zero-knowledge is the goal.

Optional later: client-side encryption so even a compromised server cannot read vault contents.

### Job B — Account / master login verification

You need a **one-way hash of the whole secret**, not per-symbol hashes, and you never “unhash.”

1. On register: `hash = Argon2id(masterPassword, salt)` → store `hash`, `salt`, params.
2. On login: recompute hash from input → **constant-time compare** to stored hash.
3. If match → issue session / unlock vault key material.
4. Optionally require **MFA** (TOTP app preferred; email/SMS OTP as fallback).

“Reverse engineer the password” is not how hashing works. Validation is **hash-and-compare**, never decrypt of a hash.

### Job C — Second factor (email / SMS / TOTP)

This is **orthogonal** to hashing. After master password succeeds:

1. Generate a short-lived random OTP (or use TOTP).
2. Deliver via email/SMS (or authenticator app).
3. User submits OTP → only then unlock vault / create session.

Prefer **TOTP (authenticator app)** as primary MFA. SMS is weaker (SIM swap) but acceptable as optional channel.

### What we keep from your idea

| Your intent | Keep as |
|---|---|
| “Complex hashing” for safety | Argon2id on the **whole** master password |
| Symbol/position mapping | Drop — leaks structure, weak crypto |
| Random string after validate | MFA OTP step (email / SMS / TOTP) |
| Feeling of a custom security service | A dedicated **Auth service** with clear boundaries |

---

## Product pillars (v1)

1. **Vault** — CRUD for credentials: label, URL, username/email, encrypted password, notes, tags.
2. **Generator** — cryptographically secure random passwords (length, charset, exclude ambiguous chars); optional passphrase mode later.
3. **Expiry & rotation** — per-item `expiresAt` / rotation policy; notify before expiry; one-click “generate + update.”
4. **Auth + MFA** — register/login with master password; optional email/SMS/TOTP challenge.
5. **Deploy modes** — same codebase: `docker compose` self-host *and* cloud deploy (e.g. Vercel/Fly + managed DB, or single VM).

---

## Non-goals (for now)

- Browser extension / autofill agent (phase 2).
- Password sharing / organizations / RBAC (phase 2).
- Breach monitoring / HaveIBeenPwned sync (phase 2).
- Replacing Bitwarden feature-for-feature.
- Custom “novel” crypto that isn’t peer-reviewed.

---

## Service architecture

One product, three logical services (can start as modules in one monorepo; split processes when needed):

```text
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Web app    │────▶│  API gateway     │────▶│  Vault service  │
│  (Next.js)  │     │  (authn/z, rate) │     │  encrypt/CRUD   │
└─────────────┘     └────────┬─────────┘     └────────┬────────┘
                             │                        │
                             ▼                        ▼
                    ┌──────────────────┐     ┌─────────────────┐
                    │  Auth service    │     │  Notify service │
                    │  Argon2id, MFA   │     │  email/SMS/cron │
                    │  sessions/JWT    │     └─────────────────┘
                    └──────────────────┘
```

| Service | Responsibility |
|---|---|
| **Web** | UI: vault, generator, settings, expiry inbox |
| **Auth** | Users, master-password verify, MFA, sessions |
| **Vault** | Encrypted items, generator API, rotation metadata |
| **Notify** | Expiry jobs, email/SMS delivery (provider adapters) |

**Self-host:** single `docker-compose` (api + web + postgres + mailer stub).  
**Cloud:** same images/config; managed Postgres + Resend/Twilio (or equivalents).

### Suggested stack (aligned with current repo)

| Layer | Choice | Notes |
|---|---|---|
| UI | Next.js (App Router) | Already present |
| Auth session | Auth.js / custom JWT | `next-auth` already in deps — evaluate vs custom |
| KDF | Argon2id | Prefer over bcrypt for new master-password hashing |
| Vault crypto | AES-256-GCM | Web Crypto or libsodium |
| DB | PostgreSQL | Credentials metadata + ciphertext |
| Jobs | Cron worker / queue | Expiry scans |
| Deploy | Docker Compose + one cloud target | Document both paths |

Keep **domain logic** free of framework/ORM types (ports at the edges). Auth and vault talk through interfaces, not each other’s tables.

---

## Domain model (MVP)

```text
User
  id, email, kdfSalt, kdfParams, passwordHash, mfaEnabled, createdAt

VaultItem
  id, userId, label, url, username,
  ciphertext, nonce, authTag,   // encrypted password (+ optional notes blob)
  expiresAt, lastRotatedAt, notifyBeforeDays,
  createdAt, updatedAt

MfaChallenge
  id, userId, channel (email|sms|totp), codeHash, expiresAt, consumedAt

NotificationOutbox
  id, userId, vaultItemId, channel, payload, status, scheduledAt
```

Generator does not need persistence unless you save “generator presets.”

---

## User flows

### Unlock

1. Enter email + master password.  
2. Auth verifies Argon2id hash.  
3. If MFA on → send/ask OTP → verify.  
4. Derive/unlock vault key → list items (decrypt on client or in trusted API for self-host simple mode).

### Add credential

1. Open “New item” → optional Generate.  
2. Save → encrypt password field → store ciphertext + expiry.

### Expiry

1. Nightly job finds items with `expiresAt` within `notifyBeforeDays`.  
2. Notify via email (SMS optional).  
3. User opens item → Generate new → Update → reset `lastRotatedAt` / `expiresAt`.

---

## Security requirements (non-negotiable)

- Never store vault passwords in plaintext.
- Never log master passwords, OTPs, or decrypted secrets.
- Constant-time compare for hashes and OTP checks.
- Rate-limit login and MFA endpoints.
- HTTPS only in cloud; document TLS for self-host.
- CSRF protection on cookie sessions; short-lived OTP (5–10 min).
- Secrets via env / secret manager, not committed.
- Threat model doc before public launch (`docs/THREAT_MODEL.md` — follow-up).

---

## MVP slice (ship this first)

Ordered delivery so each step is usable:

1. **Rename + docs** (this PR) — product identity and crypto rules.
2. **Auth skeleton** — register/login with Argon2id; session cookie.
3. **Vault CRUD** — encrypt/decrypt password field; list/detail/create/edit/delete.
4. **Generator** — move current UI onto `crypto.getRandomValues` (fix `Math.random`).
5. **Expiry fields + email notify** — cron + one email provider adapter.
6. **MFA (email OTP)** — after password OK, require code.
7. **Docker Compose** self-host path + short cloud deploy notes.

SMS MFA and TOTP can follow immediately after email OTP.

---

## Open decisions (decide before heavy coding)

1. **Zero-knowledge vs server-side decrypt**  
   - ZK: decrypt only in browser; server stores ciphertext only (harder UX, stronger cloud story).  
   - Server-side: simpler self-host MVP; trust the server.  
   **Recommendation:** server-side for MVP self-host; design ciphertext format so ZK can be added without rewriting storage.

2. **Monolith vs microservices day one**  
   **Recommendation:** modular monolith (Next.js API routes or small Nest/Fastify API) with clear packages: `auth`, `vault`, `notify`. Split only when deploy needs differ.

3. **MFA default**  
   **Recommendation:** email OTP in MVP; TOTP next; SMS optional behind Twilio-like adapter.

4. **Master password recovery**  
   No recovery without escrow = data loss. Options: recovery key downloaded once, or account reset that **wipes vault**. Document clearly in UI.

---

## Success criteria

- User can self-host with Compose and create ≥1 encrypted credential.
- Generator produces ≥16-char passwords from a CSPRNG with chosen charsets.
- Expiring item triggers at least one email notification in a test env.
- Login requires master password; with MFA enabled, also a one-time code.
- README explains Keynest, crypto stance, and both deploy modes in under 5 minutes of reading.

---

## Rename checklist

- [x] Package name → `keynest`
- [x] README rewritten for product vision
- [ ] GitHub repository rename to `keynest` (manual)
- [ ] Update Vercel/hosting project name if linked
- [ ] Update local clones: `git remote set-url ...`
