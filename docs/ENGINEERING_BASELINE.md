# Verified engineering baseline — November 11, 2026

Baseline: [`fde5612df0053d4d6d7dae9eff8fabec7f00c8bc`](https://github.com/JordyNicholas/Keynest/commit/fde5612df0053d4d6d7dae9eff8fabec7f00c8bc), default branch `main`. Delivery target: **November 28, 2026**. Tracking: [#64](https://github.com/JordyNicholas/Keynest/issues/64).

This is a source and build assessment, not a security audit or a production-readiness certification. Paths below refer to that exact commit. Proposed work is explicitly separated from verified implementation. This historical snapshot is intentionally not rewritten as later work lands; use [Subsequent adopted changes](#subsequent-adopted-changes) and the [Delivery Plan](./DELIVERY_PLAN.md) for current direction.

## Verification evidence

| Check | Evidence / result |
| --- | --- |
| Repository identity | GitHub connector confirms `JordyNicholas/Keynest`, public, default branch `main`; local clone matches the full SHA above |
| Baseline CI | [Run 35016174612](https://github.com/JordyNicholas/Keynest/actions/runs/35016174612), successful on the baseline SHA: Maven Java 21, Maven Java 25, Angular build. Java 21 job logs report 39 tests: policy 14, generator 2, Seal 12, API 11; zero failures/errors/skips |
| Local frontend | Node 24.22.1, npm 11.9.0; `npm ci --no-audit --no-fund` then `npm run build` in `apps/manager-web` passed; production initial bundle 407.28 kB |
| Local backend | Attempted with Maven 3.9.9 and Java runtime 21.0.13. Central dependencies resolved after temporary proxy configuration, but compilation was blocked: the workspace has a runtime without `javac` and the compiler reported `release version 21 not supported`. An explicit compiler-path check confirmed missing `javac`; a full JDK download timed out. No local backend test-pass claim; use the pinned CI evidence above |
| Browser / deployment | No interactive browser flow, Docker build, Compose/PostgreSQL startup, backup restore, or cloud deployment verified in this assessment; Docker is unavailable in this workspace |
| Backlog | GitHub search returned 27 open issues and no open PRs at inspection time; existing issues are retained rather than closed from documentation claims |
| Releases | GitHub releases API returned no releases; published package availability was not independently checked |
| Repository rules | Active `main - owner merge` ruleset requires a PR, one approving review, stale-review dismissal, and last-push approval. It has a repository-role PR bypass; no required-status-check rule is present in that ruleset. Legacy branch-protection settings were not inspected |

Reproduction from repository root: `mvn -B test`; then `cd apps/manager-web`, `npm ci`, `npm run build`. CI uses Node 22; the local frontend result uses the environment's Node 24. Do not confuse the two environments.

## Subsequent adopted changes

These entries occurred after the pinned baseline and do not alter its historical findings:

- `[verified]` PR [#67](https://github.com/JordyNicholas/Keynest/pull/67) adopted the shared [Project Charter](./PROJECT_CHARTER.md), repository-wide agent instructions, and proposed [AI Architecture](./AI_ARCHITECTURE.md).
- `[verified]` Maintainer-approved PR [#68](https://github.com/JordyNicholas/Keynest/pull/68) completed issue #30: deployed modes reject missing/demo vault keys, new envelopes bind owner/item/policy using AAD v1, and legacy policy-only envelopes remain readable until resealed. CI passed Java 21, Java 25, Angular, and 48 Java tests. See accepted [ADR-0006](./ADR-0006-manager-vault-key-and-aad.md).
- `[verified]` PR [#70](https://github.com/JordyNicholas/Keynest/pull/70) reconciled the shared charter, pinned baseline and active delivery plan; it accepted the charter and ADR-0006 without rewriting the historical snapshot.
- `[verified]` Maintainer-approved PR [#72](https://github.com/JordyNicholas/Keynest/pull/72) completed issue #71: the API stores only session-token digests, Manager Web uses tab-scoped storage and legacy sessions are intentionally revoked. Java 21, Java 25 and Angular CI passed. See accepted [ADR-0007](./ADR-0007-session-token-storage-and-browser-transport.md).
- `[verified]` Maintainer-approved PR [#74](https://github.com/JordyNicholas/Keynest/pull/74) completed issue #73: secret reveal is an authenticated POST with no-store headers, cross-user denial coverage and a 15-second client disclosure window. Java 21, Java 25 and Angular CI passed.

## What exists

| Area | Verified implementation | Practical limit |
| --- | --- | --- |
| Libraries | Maven reactor includes policy, generator, Seal, and Manager API; libraries have no Spring dependencies | No external sample consumer; root manages both library and Boot dependencies |
| Policy / generation | JSON Schema loader, registry, lifecycle validation, SecureRandom generator, invariant tests | No AI policy authoring or provider integration |
| Seal | AES-256-GCM, Argon2id password hashing, binary v1 envelope, golden-vector tests, re-encrypting `rewrap`, intermediate-buffer cleanup | No Manager key-provider abstraction or per-user wrapped DEK; `rewrap` decrypts/reseals, it is not KEK-only rotation |
| API | Auth registration/login/logout, database sessions, owner-scoped vault queries, create/read/update/delete, generated creation, expiry metadata | HTTP and JPA concerns remain in application services; no migrations or notification worker |
| UI | Angular 21, routed Tools/Vault/Account shell, Material dependencies/theme, forest tokens and fade motion | Tools uses a Material button, but most page forms remain native controls; vault supports list/generated-create/reveal/delete, lacks editing and guided rotation |
| Deployment | API multistage Dockerfile, PostgreSQL 16 + API Compose, Vercel build configuration | Web is absent from Compose; production API URL is a Railway placeholder; defaults are demo-oriented |
| Tests / CI | Java library tests and API MockMvc/H2 tests; Java 21/25 tests and Angular build in CI | No frontend spec files at baseline, no PostgreSQL integration test, no browser end-to-end test, no packaging/container smoke gate |

Primary evidence: [root POM](../pom.xml), [CI](../.github/workflows/ci.yml), [Seal](../libs/keynest-seal/src/main/java/dev/keynest/seal/KeynestSeal.java), [API](../apps/manager-api/src/main/java/dev/keynest/manager), [web](../apps/manager-web/src/app), [deployment](../deploy).

## Release blockers and engineering gaps

Priority means implementation order for this delivery, not an externally assigned vulnerability score. A source finding is not proof of remote exploitability in an unknown deployment.

| Priority | Finding and source | Required outcome / tracking |
| --- | --- | --- |
| P0 | Default and `docker` properties supply a fixed vault key; Compose injects the same value. `VaultService` only checks decoded length | Explicit development-only demo configuration; deployed mode rejects missing/demo keys. Start with [#30](https://github.com/JordyNicholas/Keynest/issues/30); add key-custody ADR and migration plan |
| P0 | `VaultService.sealPassword/openPassword` binds only policy ID as AAD, under one server-wide key | Bind canonical owner/item/policy context; test envelope substitution rejection and migration of existing data before changing formats/context |
| P0 | `AuthSession.token` stores usable bearer tokens; `AuthService` looks them up directly. Web `AuthService` persists bearer tokens in localStorage | Store session digests; decide browser transport, expiration and revocation in an ADR. If cookies are selected, cover CSRF and cross-origin deployment. Invalidate/migrate old sessions explicitly |
| P0 | `VaultController.get` accepts `reveal=true` on GET; controller sets no explicit no-store header. Web retains revealed values in a map | Explicit authenticated POST reveal, no-store responses, bounded reveal lifetime and clearing; cover auth failure, logout, navigation, and cross-user access |
| P0 | `RateLimitFilter.clientIp` accepts arbitrary `X-Forwarded-For`; counters are per process | Define trusted-proxy handling, bounded resource use and single/multiple replica behavior; tests for spoofed headers and auth abuse |
| P0 | `VaultAuthIntegrationTest` covers one user's happy path on H2 | Add cross-user list/get/reveal/update/delete denial, invalid/expired/revoked sessions, key configuration and AAD-negative tests |
| P1 | JPA `ddl-auto=update` in both profiles; no migration files; PostgreSQL not exercised by tests | Versioned migrations, PostgreSQL integration checks, upgrade and restore procedure before a release containing real data |
| P1 | Labels, URLs and usernames are plaintext columns in `VaultItem`; server opens passwords into Strings | Document metadata disclosure and server trust explicitly; decide encrypted payload scope before promising vault-wide encryption. Expand [#43](https://github.com/JordyNicholas/Keynest/issues/43) |
| P1 | Registration returns explicit account conflict; login skips hash verification for unknown users | Review enumeration, timing, validation bounds and error contracts with meaningful tests; no unverified constant-time claim |
| P1 | No AI/MCP module, BYOK credential lifecycle, agent scopes, approval workflow, or AI data policy | Define ports and threat model before provider/network integration; smallest useful slice is policy explanation/proposal with user-owned credentials |
| P1 | No root LICENSE or SECURITY.md; threat model is a stub; CI actions use version tags; no dependency-update/SBOM workflow | Maintainer chooses license; add disclosure policy, dependency review and release artifacts. Do not invent a vulnerability-reporting address |
| P1 | Web has no tests; raw forms, fixed demo login values, hard-coded creation expiry and unlimited reveal state | Complete accessible Material flows and critical-flow tests under [#26](https://github.com/JordyNicholas/Keynest/issues/26), [#31–35](https://github.com/JordyNicholas/Keynest/issues/31); respect reduced-motion preferences |
| P1 | No notify scheduler/outbox/mail adapter despite persisted expiry fields | Implement bounded, idempotent notification flow under [#36–39](https://github.com/JordyNicholas/Keynest/issues/36), without secret payloads |
| P1 | `src/environments/prod.ts` contains a Railway placeholder; Compose has no web service | Runtime configuration or portable web build, documented TLS topology, complete local path and external consumer under [#40–42](https://github.com/JordyNicholas/Keynest/issues/40) |

## Architecture assessment and direction

**Retain the modular monolith and public libraries.** The existing Java 21 / Spring Boot 4 / Angular 21 structure supports the product. Incremental boundary improvements are preferable to a framework rewrite. These are design proposals for implementation PRs, not claims about current behavior.

| Boundary | Direction |
| --- | --- |
| Pure libraries | Keep Seal, policy and generation usable without Manager, Spring, persistence, an LLM, or a network connection. Preserve golden vectors and public contracts; independently verify any compatibility change |
| Manager application | Organize auth, vault, notify, AI and agent capabilities by feature. Extract application use cases and small ports where external concerns exist. Web DTOs and JPA entities stay in adapters; do not create abstractions solely to wrap every class |
| Key custody | Introduce a KeyProvider port with portable file/environment bootstrap support, then evaluate per-user random DEKs wrapped by a KEK. Include key IDs, versioned binding context, rewrap/migration, backup and recovery semantics. KMS/Vault adapters are optional infrastructure, not required for local use |
| AI / BYOK | BYOK means user-supplied **AI provider credentials**, separate from vault encryption keys. Use an AiProvider port and provider-neutral request/response contracts. Deterministic policy validation remains authoritative. Begin with explicit, ephemeral credential use; persistent storage requires encrypted custody and deletion/revocation design |
| Agent gateway | Scoped agent identities call allowlisted application use cases. Models receive only explicitly selected non-secret inputs and sanitized results. Secret reveal and provider credentials never enter prompts, tool outputs, traces or transcripts. Mutations require server-validated plans and explicit human approval; bind approval to actor, action, target/version and expiry to prevent replay |
| Infrastructure | One API artifact, portable PostgreSQL, configurable browser API origin and optional adapters for AI, mail and key custody. Local operation must not require Railway, Vercel, a cloud KMS, or an AI provider |
| UX | Preserve forest branding and Material primitives; finish task flows before decorative motion. Keyboard access, focus, errors, loading/empty states, responsive layout and reduced motion are release criteria |
| Observability | Structured sanitized events and correlation IDs; audit auth/key/agent mutations without request bodies, credentials or secret values. Start with health/readiness and actionable counters; external telemetry is optional |

AI failure, missing credentials, or disabled AI must not disable vault CRUD or deterministic tools. A provider adapter must constrain destinations and redirects so user configuration cannot turn server-side AI requests into arbitrary internal-network access. Treat model output as untrusted proposals; validate it against the existing schema and authorized use cases.

## Documentation and backlog reconciliation

- [PRODUCT](./PRODUCT.md) and [ROADMAP](./ROADMAP.md) describe a mixture of target and earlier status. Use this document for the pinned observed state and [DELIVERY_PLAN](./DELIVERY_PLAN.md) for current sequencing.
- Material routing has landed, but [#25](https://github.com/JordyNicholas/Keynest/issues/25) still has remaining Material polish. Do not mark it complete because routes exist.
- Library `rewrap` and buffer cleanup exist and have tests; threat-model entries listing them as missing are stale. Manager rotation/key custody remains separate work.
- At the pinned baseline, [#30](https://github.com/JordyNicholas/Keynest/issues/30) and [#43](https://github.com/JordyNicholas/Keynest/issues/43) were open. PR #68 later completed #30 as recorded above; #43 remains the release pass. Add focused issues for session/reveal/proxy/AI work when implementing; avoid duplicate epics.
- `reset wipes vault` is a documented recovery direction, not an implemented reset endpoint. MFA, recovery keys, zero knowledge and organization sharing remain future work unless explicitly rescheduled.

## Verification limits

A passing build cannot establish encryption custody, tenant isolation, trustworthy agent approvals, accessibility, deployment readiness or license suitability. The release plan requires targeted negative tests, PostgreSQL and browser verification, and a clean-machine operational rehearsal. No security-sensitive changes are merged by this assessment.
