# Keynest

Self-hostable **password tools** you can embed, plus a first-party **Manager** vault that dogfoods them.

**Tagline:** Password tools you can embed — and a vault manager that uses them.

> Detailed grooming: [docs/PRODUCT.md](./docs/PRODUCT.md) · ADRs in [`docs/`](./docs/)

---

## Product definition

### Vision

Ship reusable password-safety libraries (seal/open, policy validate, generate, rotate helpers) that any external service can adopt — and build **Keynest Manager** on those same libraries as the vault product (self-host or cloud).

### Two offerings

| Offering | What it is | Who uses it |
|---|---|---|
| **Keynest Tools** | Versioned Java libraries (`keynest-seal`, `keynest-policy`, `keynest-generate`) | Any backend / integrator |
| **Keynest Manager** | Angular UI + Spring Boot API vault product | End users; also the reference consumer of the tools |

The Manager must call the **same** public library APIs external services would. No private shortcuts.

### Problems we solve

**Integrators**

1. Seal secrets for storage/transit with a versioned envelope.
2. Validate candidates against enforceable standards (same rules as generate).
3. Generate passwords that always pass those standards.
4. Update / rotate with policy-aware validation before re-sealing.

**End users (Manager)**

1. Store login data (URL, username/email, password).
2. Create / read / update / delete vault items.
3. Manage expiry (notify + guided update).
4. Unlock safely (master credential + optional MFA).
5. Deploy self-hosted or cloud.

### Security stance

| Unique (product IP) | Not unique / not safe |
|---|---|
| Versioned **Keynest Seal** envelope + API | Inventing a new cipher |
| Schema-backed **password policies** shared by generate & update | Per-character hashing |
| Argon2id hash/verify for auth; AES-256-GCM for vault secrets | “Reverse engineering” hashes |

Primitives stay standard: Argon2id, AES-256-GCM, CSPRNG. See [ADR-0001](./docs/ADR-0001-crypto-model.md), [ADR-0003](./docs/ADR-0003-keynest-seal.md).

### Password policy schemas (Zod-like contract)

Password standards are **JSON documents** validated by a JSON Schema (Java’s practical stand-in for Zod):

| Artifact | Path |
|---|---|
| Meta-schema | [`docs/schemas/password-policy.schema.json`](./docs/schemas/password-policy.schema.json) (also on classpath) |
| Default policy | [`docs/policies/keynest.default.v1.json`](./docs/policies/keynest.default.v1.json) |
| Loader / registry | `PasswordPolicyLoader`, `PolicyRegistry` |
| Lifecycle façade | `PasswordStandards` (`validateForCreate` / `Update` / `Rotate` / generate check) |

`generate` and `update` both resolve a policy by id and run the same `PasswordValidator`. Details: [ADR-0004](./docs/ADR-0004-password-policy-schema.md).

### Target stack

| Layer | Choice |
|---|---|
| Tools + Manager API | Java 21, Spring Boot 3, Maven multi-module |
| Manager UI | Angular |
| DB (next) | PostgreSQL |
| Deploy | Docker Compose + cloud profile |

---

## Repo layout

```text
libs/keynest-policy      schemas + validate + lifecycle skeleton
libs/keynest-generate    CSPRNG generate under the same policy
libs/keynest-seal        Seal envelope (AES-256-GCM) + Argon2id
apps/manager-api         Spring Boot façade (dogfoods libs)
apps/manager-web         Angular Manager shell
deploy/                  Docker Compose + API Dockerfile
docs/                    PRODUCT, ADRs, published schema copies
archive/next-prototype   legacy Next.js app (do not extend)
```

---

## Prerequisites

- JDK 21+, Maven 3.9+
- Node 20+ (Angular Manager UI)

## Build & test

```bash
mvn test
```

### Run API

```bash
mvn -pl apps/manager-api spring-boot:run
```

Tools endpoints:

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/tools/health` | Liveness + default policy id |
| `GET` | `/api/v1/tools/policy` | Schema-backed policy document (`?policyId=`) |
| `POST` | `/api/v1/tools/generate` | `?policyId=` (must allow `generate`) |
| `POST` | `/api/v1/tools/validate` | body + `?operation=create\|update\|rotate\|generate` |
| `POST` | `/api/v1/tools/seal` / `open` | Seal demo (dev key only) |
| `POST` | `/api/v1/tools/hash-password` / `verify-password` | Argon2id |

### Run Manager UI

```bash
cd apps/manager-web
npm install
npm start
```

Open http://localhost:4200 (calls API on :8080).

### Docker (API)

```bash
docker compose -f deploy/docker-compose.yml up --build
```

### Vercel (Manager Web)

Root [`vercel.json`](./vercel.json) runs `npm run build`, which builds **`apps/manager-web`** and emits Vercel Build Output under `.vercel/output/static` (see [`scripts/prepare-vercel-output.js`](./scripts/prepare-vercel-output.js)).

Optional: set Project Root Directory to `apps/manager-web` in the Vercel dashboard for a direct Angular preset.

---

## MVP status

1. Grooming docs + stack decision — done  
2. Maven libs + Spring/Angular skeleton — done  
3. **Password policy JSON Schema + loader skeleton** — done  
4. Next: Postgres vault + auth, Seal golden vectors, expiry notify, external sample consumer  

## Rename

Target GitHub repository name: **`keynest`** (Settings → General).
