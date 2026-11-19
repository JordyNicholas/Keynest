# Keynest

**Engineering execution:** [Canonical product and development roadmap](docs/MASTER_ROADMAP.md) · [November MVP evidence](docs/MVP_RELEASE_EVIDENCE.md)

Self-hostable **password tools** you can embed, plus a first-party **Manager** vault that dogfoods them.

**Tagline:** Password tools you can embed — and a vault manager that uses them.

**Release targets:** November 21 internal MVP checkpoint and November 28 public release, subject to the [release gates](./docs/MASTER_ROADMAP.md).

> Project charter: [docs/PROJECT_CHARTER.md](./docs/PROJECT_CHARTER.md) · Verified baseline: [docs/ENGINEERING_BASELINE.md](./docs/ENGINEERING_BASELINE.md) · Delivery plan: [docs/DELIVERY_PLAN.md](./docs/DELIVERY_PLAN.md) · AI boundaries: [docs/AI_ARCHITECTURE.md](./docs/AI_ARCHITECTURE.md) · Product: [docs/PRODUCT.md](./docs/PRODUCT.md) · Board: [docs/reference/board.md](./docs/reference/board.md) · ADRs in [`docs/`](./docs/)

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

Try the [standalone Java consumer](./examples/external-consumer/README.md): it generates/validates, seals/opens and rewraps using only the three libraries, with Manager/Spring dependencies explicitly banned. Build the libraries from source and install them into your local Maven repository to try the example.

### Library distribution

Keynest `0.1.0-rc.1` uses source distribution. From a checkout or source archive of the candidate revision, follow the standalone example to build and install the three libraries into your local Maven repository. A successful run verifies generation and validation, sealing and opening, rewrapping, and wrong-key and wrong-context rejection using only the library dependencies.

GitHub Packages is planned as the first registry. Registry distribution requires publishing the Maven parent and the three libraries, then verifying the standalone consumer from a clean Maven cache. Maven Central publication remains future work. See [GitHub’s Maven registry guide](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry).

### Problems we solve

**Integrators**

1. Seal secrets for storage/transit with a versioned envelope.
2. Validate candidates against enforceable standards (same rules as generate).
3. Generate passwords that always pass those standards.
4. Update / rotate with policy-aware validation before re-sealing.

**End users (Manager)**

1. Store login data (URL, username/email, password).
2. Create / read / update / delete vault items.
3. View upcoming and overdue expiries and follow guided rotation.
4. Sign in with a master password.
5. Deploy self-hosted or cloud.

### Security stance

| Unique (product IP) | Not unique / not safe |
|---|---|
| Versioned **Keynest Seal** envelope + API | Inventing a new cipher |
| Schema-backed **password policies** shared by generate & update | Per-character hashing |
| Argon2id hash/verify for auth; AES-256-GCM for vault secrets | “Reverse engineering” hashes |

Primitives stay standard: Argon2id, AES-256-GCM, CSPRNG. See [ADR-0001](./docs/ADR-0001-crypto-model.md), [ADR-0003](./docs/ADR-0003-keynest-seal.md), and the source-informed release [threat model](./docs/THREAT_MODEL.md).

### Current release limitations

Manager decrypts vault passwords server-side using a server-wide key. Vault metadata is plaintext in the database, and browser JavaScript can read the tab-scoped session bearer. Rate limits apply per API instance. Security events are operational logs. Keep database backups and the matching vault key separately.

Manager Web starts separately from Compose. The reminder adapter simulates delivery locally. BYOK policy explanation is off by default and uses third-party processing when enabled. Live-provider verification remains pending. MFA and agent tools are outside this release scope. See the [threat model](./docs/THREAT_MODEL.md) and [MVP evidence checklist](./docs/MVP_RELEASE_EVIDENCE.md).

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
| Tools + Manager API | Java 21 LTS (`--release 21`), Spring Boot 4, Maven multi-module |
| Manager UI | Angular 21 LTS |
| DB (next) | PostgreSQL |
| Deploy | Docker Compose + cloud profile |

Toolchain policy: [ADR-0005](./docs/ADR-0005-java-and-toolchain-policy.md).

---

## Repo layout

```text
libs/keynest-policy      schemas + validate + lifecycle skeleton
libs/keynest-generate    CSPRNG generate under the same policy
libs/keynest-seal        Seal envelope (AES-256-GCM) + Argon2id
apps/manager-api         Spring Boot façade (dogfoods libs)
apps/manager-web         Angular Manager shell
deploy/                  Docker Compose + API Dockerfile
docs/                    PRODUCT, ADRs, coding standards, published schema copies
```

Indentation and formatting: root [`.editorconfig`](./.editorconfig) — **2 spaces** (see [docs/CODING_STANDARDS.md](./docs/CODING_STANDARDS.md)).

---

## Prerequisites

- **JDK 21 LTS** (primary runtime; CI also tests on JDK 25 with bytecode `--release 21`)
- Maven 3.9+
- Node.js 22 and npm (Manager Web; matches CI)

See [ADR-0005](./docs/ADR-0005-java-and-toolchain-policy.md) for version policy.

## Build & test

```bash
mvn test
```

### Run API

```bash
mvn -pl apps/manager-api -am package -DskipTests
java -jar apps/manager-api/target/manager-api-0.1.0-rc.1.jar
```

The standalone API listens on `127.0.0.1:8080` by default. Use the [hosted Compose guide](./docs/DEPLOYMENT.md) for a deployment that serves other machines.

Tools endpoints:

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/tools/health` | Liveness + default policy id |
| `GET` | `/api/v1/tools/policy` | Schema-backed policy document (`?policyId=`) |
| `POST` | `/api/v1/tools/generate` | `?policyId=` (must allow `generate`) |
| `POST` | `/api/v1/tools/validate` | body + `?operation=create\|update\|rotate\|generate` |
| `POST` | `/api/v1/tools/seal` / `open` | **Local demo only** — gated (see below) |
| `POST` | `/api/v1/tools/hash-password` / `verify-password` | Argon2id |

The HTTP verification tool accepts Argon2id hashes within a per-request budget of 64 MiB, three iterations and one lane. It returns `valid: false` for unsupported hashes. Use the Java library when you need to verify hashes created with a different work factor.

**Tools demo seal/open (#16):** Local `application.properties` sets `keynest.tools.demo.enabled=true` with a fixed hex DEK for dogfooding. The `docker` profile sets `enabled=false` by default and does **not** ship a demo key — seal/open return **403**. Override only for intentional non-prod demos: `KEYNEST_TOOLS_DEMO_ENABLED=true` plus `keynest.tools.demo.key-hex` / `key-id`.

**Manager vault key (#30):** The API fails startup unless the vault key is valid 32-byte hex. The public demo value is accepted only with the explicit development setting `keynest.vault.allow-demo-key=true`; `prod`/`production` profiles always reject it. Local Compose sets `KEYNEST_ALLOW_DEMO_VAULT_KEY=true` visibly for demonstration. Any deployed environment must provide a randomly generated `KEYNEST_VAULT_KEY_HEX`, leave `KEYNEST_ALLOW_DEMO_VAULT_KEY` unset/false, set a stable `KEYNEST_VAULT_KEY_ID`, and activate `prod` alongside its database profile. Preserve and back up that key: changing or losing it makes existing envelopes unreadable. See [ADR-0006](./docs/ADR-0006-manager-vault-key-and-aad.md).

**Rate limits (#17, #75):** The tools `validate`, `hash-password`, and `verify-password` and the auth routes `register` and `login` each have an in-memory fixed window (default **30 requests per 60 seconds** per client IP and route). Over limit or active-identity capacity exhaustion → **429** + `Retry-After`. By default, identity is the direct socket peer and caller-supplied `X-Forwarded-For` is ignored. Behind a reverse proxy, set `KEYNEST_RATE_LIMIT_TRUSTED_PROXIES` to a comma-separated list of the proxy IPs/CIDRs; Keynest then walks the chain from the nearest hop and stops at the first untrusted address. Never use `0.0.0.0/0` or `::/0` merely to make forwarding work. Configure bounds with `KEYNEST_RATE_LIMIT_MAX_ENTRIES` (default 10,000) and `KEYNEST_RATE_LIMIT_MAX_FORWARDED_FOR_ENTRIES` (default 16). Limits remain **per JVM instance** and are not globally consistent across replicas; use a shared edge/distributed limiter when that guarantee is required. Paths live in `ApiPaths.RATE_LIMITED_POSTS`.

### Run Manager UI

```bash
cd apps/manager-web
npm install
npm start
```

Open http://localhost:4200. Development uses `http://localhost:8080/api/v1`. Production defaults to the same-origin `/api/v1`; set the public build variable `KEYNEST_API_BASE_URL` for a separately hosted API without editing source. Use `npm run build` so the destination is validated. See [deployment configuration](./docs/VERCEL.md) for same-origin, local, and split-origin hosting.

### Docker (API)

```bash
docker compose -f deploy/docker-compose.yml up --build
```

Uses Spring profile `docker` (Postgres) with an explicit local demo-key opt-in. For a Railway-style API + Postgres deployment, activate `docker,prod`; set `KEYNEST_JDBC_URL` (or host pieces), `KEYNEST_CORS_ALLOWED_ORIGINS` to your web origin, a non-demo `KEYNEST_VAULT_KEY_HEX`, a stable `KEYNEST_VAULT_KEY_ID`, and rely on `PORT`. Do not set `KEYNEST_ALLOW_DEMO_VAULT_KEY`. Tools demo seal/open stay **off** unless you explicitly set `KEYNEST_TOOLS_DEMO_ENABLED`. Rate limits stay **on** by default; configure `KEYNEST_RATE_LIMIT_TRUSTED_PROXIES` only from the hosting provider's documented proxy network and tune the other `KEYNEST_RATE_LIMIT_*` bounds as needed.

See the [Compose runbook](./docs/COMPOSE.md) for prerequisites, first startup, separate web setup, disposable HTTP smoke testing, data-preserving shutdown and known deployment gaps. Compose includes API + PostgreSQL; Manager Web is not in the stack.

### Vercel (Manager Web)

The Manager UI is Angular under **`apps/manager-web`**. The linked Vercel project must **not** use the Next.js preset (that is what caused `No Next.js version detected`).

**Dashboard (required once):**

1. Vercel → Project **fep-password** → **Settings** → **General**
2. **Root Directory** → `apps/manager-web`
3. **Framework Preset** → **Angular** (turn off overrides that still run `next build`)
4. Redeploy the latest PR commit

Repo config: root [`vercel.json`](./vercel.json) and [`apps/manager-web/vercel.json`](./apps/manager-web/vercel.json) both declare `framework: "angular"` and the Angular `dist/manager-web/browser` output.

---

## MVP status

Manager includes account registration and sessions, manual and generated vault creation, metadata editing, timed reveal, confirmed deletion and password rotation, and expiry visibility. Tools can generate passwords and validate samples. See the [MVP release evidence](./docs/MVP_RELEASE_EVIDENCE.md) for verification status and open release gates.

## Manager API (auth and vault)

Use the API startup commands above.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Argon2id master hash |
| `POST` | `/api/v1/auth/login` | Returns bearer token |
| `POST` | `/api/v1/auth/logout` | |
| `POST` | `/api/v1/auth/me` | |
| `GET/POST` | `/api/v1/vault/items` | List / create (optional `generate:true`) |
| `GET` | `/api/v1/vault/items/{id}` | Read non-secret item metadata |
| `POST` | `/api/v1/vault/items/{id}/reveal` | Open sealed password; response is `no-store` |
| `PUT/DELETE` | `/api/v1/vault/items/{id}` | Update / delete |

**Session security (#71):** Manager returns a 256-bit opaque bearer token, while the API stores only its SHA-256 digest. Flyway V2 names the physical column `auth_sessions.token_digest`; the pre-Flyway `token` column held the same digest values after #71. Manager Web keeps the bearer in `sessionStorage`, clears legacy `localStorage`, and required sign-in again when #71 invalidated older raw-token sessions. This reduces database and browser-persistence exposure; it does not make JavaScript-held bearers immune to XSS. See [ADR-0007](./docs/ADR-0007-session-token-storage-and-browser-transport.md).

Postgres stack:

```bash
docker compose -f deploy/docker-compose.yml up --build
```

**Database migrations (#79):** Flyway owns schema creation and upgrades; Hibernate runs in validation-only mode. Fresh databases execute the versioned schema normally. A pre-Flyway non-empty database is baselined at V1 and then upgraded, including the `auth_sessions.token` → `token_digest` rename. This upgrade is not application-only reversible: stop writers and complete the backup, orphan-row preflight, single-instance migration and verification steps in [the database migration runbook](./docs/DATABASE_MIGRATIONS.md) before deploying. See [ADR-0008](./docs/ADR-0008-versioned-database-migrations.md).

**Health probes (#82):** `/actuator/health/liveness` checks process availability; `/actuator/health/readiness` also checks the database. Responses expose only status, with HTTP 503 when unavailable. Diagnostic endpoints and discovery are disabled. See [probe operation and routing](./docs/OPERATIONS.md).

**Optional AI backend (#97):** The authenticated, disabled-by-default policy explanation backend uses an ephemeral user-supplied provider key and a fixed-destination provider adapter. Browser workflow and live-provider verification remain pending; see [contract, configuration and evidence gates](./docs/AI_BACKEND.md). Core vault/tools require no AI provider or key.

**Security events (#92):** Auth/vault HTTP outcomes use a fixed-field logger that omits request/response payloads and user/resource identifiers. This is best-effort operational visibility; durable/attributed auditing remains future work. See [event schema, privacy and operator controls](./docs/SECURITY_EVENTS.md) and accepted [ADR-0009](./docs/ADR-0009-sanitized-http-security-events.md).

## Rename

Target GitHub repository name: **`keynest`** (Settings → General).
