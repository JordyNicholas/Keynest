# Keynest — Canonical Product & Engineering Roadmap

**Status:** Active, maintainer-directed execution plan  
**Updated:** November 15, 2026 (America/Sao_Paulo)  
**Internal MVP checkpoint:** **Saturday, November 21, 2026**  
**Portfolio-ready open-source release target:** **Saturday, November 28, 2026**  
**After November 28:** staged follow-on releases; no invented delivery dates

> **Dedicated agent: start here.** This is the authoritative **scope, sequence, dependency and acceptance-gate** roadmap for Keynest. It consolidates prior product discussions, the accepted charter and the older phased plans. It does **not** certify features as complete. Read [AGENTS.md](../AGENTS.md), [PROJECT_CHARTER.md](PROJECT_CHARTER.md), [PRODUCT.md](PRODUCT.md), accepted ADRs, [THREAT_MODEL.md](THREAT_MODEL.md), [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md), [DELIVERY_PLAN.md](DELIVERY_PLAN.md), [MVP_RELEASE_EVIDENCE.md](MVP_RELEASE_EVIDENCE.md), and [PROJECT_PR_WORKFLOW.md](PROJECT_PR_WORKFLOW.md). Where old dates or feature scope disagree, this roadmap governs **execution sequencing**; accepted ADRs and explicit maintainer decisions govern technical/security contracts.

## 1. Product vision and target audiences

**Keynest is a security-first, AI-native but AI-optional, BYOK-first, infrastructure-agnostic, open-source developer and small-team secrets/password-management platform.** It has two complementary products:

1. **Embeddable libraries and portable contracts:** `keynest-seal` (versioned AES-256-GCM envelopes and Argon2id), `keynest-policy` (schema-backed policy validation), `keynest-generate` (CSPRNG generation). A standalone consumer must work without Spring, Manager or AI. Over time, stabilize language-neutral specifications/golden vectors.
2. **Keynest Manager:** a secure Java 21/Spring Boot 4 API with PostgreSQL, a polished Angular 21/Material-inspired browser experience, and portable container-based deployment. Serve developers, integrators, self-hosters and small engineering teams first; enterprise customers are a later expansion.

**Product differentiation:** the same deterministic policy drives generation, validation and rotation; a reusable versioned sealing format; practical self-hosted UX; safe AI-assisted policy understanding using **user-provided AI provider keys (BYOK)**. Long-term research direction: a **policy-driven Agent Gateway** that performs narrowly scoped operations without exposing vault secrets to model context. Do not present this hypothesis as already shipped or unique without evidence.

**Non-negotiable invariants:**
- **Security and correctness outrank deadlines.** No custom cryptographic primitives, unreviewed key/envelope changes, plaintext logs, broken tenant isolation, unsafe defaults, or false security claims.
- **Deterministic authority.** Backend code decides authentication, authorization, ownership, password-policy validity and allowed operations. LLM output is untrusted advice, never permission or executable authority.
- **AI optional.** Vault, generation, validation, rotation and deployment function with AI disabled, without an external LLM and without a specific cloud vendor.
- **BYOK and provider independence.** AI provider credentials are distinct from vault encryption keys; use request-scoped ephemeral credentials first. Provider adapters are behind a typed port. No provider-specific concepts in core policy or vault logic.
- **Portable architecture.** Retain the modular monolith and independent libraries; use explicit ports only at meaningful boundaries (keys, providers, notifications, audit). Prefer PostgreSQL + Compose and a separately hosted Angular web app over mandatory cloud services.
- **Product quality.** Keynest's forest-green/typography identity with Angular Material interaction patterns, responsive layouts, keyboard/focus/contrast/reduced-motion accessibility, clear errors and intentional motion.
- **Evidence over promises.** A merged PR, test source file, green Vercel status or checklist is not proof of a successful full-stack/security rehearsal. Avoid claims of zero knowledge, independent audit, compliance, enterprise readiness or universal provider support.
- **Incremental improvement.** Retain/refactor/replace based on source and runtime evidence; no speculative full rewrite, premature microservices, or needless abstraction.

## 2. Baseline, classification and working rules

At the November 15 inspection, `main` pointed to [`6d53654`](https://github.com/JordyNicholas/Keynest/commit/6d53654bba068b59bbe238aefe9e7aff2e70bc15). Source and docs include auth/vault security hardening, Flyway/PostgreSQL, container checks, independent library example, Angular vault/rotation/expiry features, optional ephemeral BYOK explanation backend and UI, and reminder outbox/console simulation. This is **observed implementation**, not a claim that every journey passed. [PR #119 CI](https://github.com/JordyNicholas/Keynest/actions/runs/43629011117) is recorded as a successful baseline in the [MVP evidence](MVP_RELEASE_EVIDENCE.md); collect **new exact-SHA evidence** for the release candidate.

Classification used throughout:
- **P0 / must fix:** security/correctness, essential auth/vault flow, real database/deploy/recovery, current CI evidence, approved root license and vulnerability disclosure. Failing P0 blocks public release.
- **P1 / release quality:** accessible/coherent UX, docs, reproducible onboarding, library distribution honesty, portfolio demo and packaging.
- **P2 / optional:** narrow BYOK demo, local reminder rehearsal and extra polish. Defer or disable if they endanger P0/P1.
- **P3 / post-release:** enterprise, advanced recovery/MFA, cloud KMS, broad agent/MCP, zero knowledge and other expansion.

Report status as **verified (SHA + link)**, **implemented/unverified**, **planned**, **blocked by decision**, or **deferred**. Reconcile open issue definitions with actual code; do not assume an open issue proves missing functionality or a merged PR proves runtime success.

## 3. November 2026 release contract

| Surface | Must demonstrate by Nov 28 | May ship only with explicit limitation | Post-release |
| --- | --- | --- | --- |
| Libraries | Versioned Seal/policy/generate, tests/golden vectors, independent Java consumer, clear API/docs | No published Maven Central artifact if accurately described and local consumer reproducible | Other language SDKs and HTTP sidecar |
| Security/API | Production key guard, Argon2id auth, session revocation/digests, owner-scoped CRUD, no-store bounded reveal, safe migrations, key-backed recovery | Server-side decrypt, one server-wide key, legacy AAD migration debt, plaintext metadata, JS-held session bearer | Per-user wrapped DEKs, KMS, client-side ZK |
| Web | Register/login/logout, create/list/edit/delete/reveal/conceal, generate/validate, guided rotate, expiry list, responsive accessible errors | No full consumer password-manager parity | Browser extension, advanced sharing |
| Deployment | Clean PostgreSQL + API + Angular setup, real backup/restore with retained key, reproducible HTTP/browser smoke | Web separately started from Compose, per-instance rate limits, limited audit | Kubernetes, HA, hosted multi-region |
| Governance | Root `LICENSE`, `SECURITY.md` with private reporting, threat/limitations review, changelog and exact-SHA CI evidence | No independent security audit/certification | Enterprise compliance program |
| Expiry | Expiry visibility and guided rotation | Console/local reminder simulation **is not external email** | Verified opt-in email and multi-channel delivery |
| AI | Core works with AI disabled; optional narrow BYOK read-only policy explanation | If live provider unverified, keep disabled or clearly experimental | Multi-provider breadth, policy writes, MCP/agent tools |
| Account/team | Safe documented session/recovery behavior | No MFA, advanced recovery, org RBAC or SSO | MFA, recovery keys, enterprise roles/OIDC |

**Security cannot be waived for a date.** November 21 is an **internal MVP evidence checkpoint**; November 28 is the public-release target **only if P0 gates pass**. If they do not, keep the candidate internal, document the blocker and make a maintainer go/no-go decision.

## 4. Execution roadmap, from foundation to mature product

### Phase 0 — Foundation and verified baseline (historical; repeat before each task)

**Goal:** one shared engineering source of truth.
- Inspect latest `main`, open PRs, branches, issues, relevant accepted ADRs and CI before changing files. Avoid overlapping another agent's work.
- Preserve Java 21/Spring Boot 4/Angular 21/PostgreSQL, independent libraries, clean module boundaries and versioned contracts.
- Keep [ENGINEERING_BASELINE.md](ENGINEERING_BASELINE.md) pinned to its original SHA; do not rewrite history. Add ADRs for consequential changes.
- Confirm the task's issue, dependency, scope, rollback and test plan before implementing.

**Exit:** current SHA and affected contracts identified; no conflicting work; issue and acceptance criteria defined.

### Phase 1 — Crypto, auth, key custody and data integrity (P0; harden and verify)

**Goal:** a defensible secrets-management trust boundary.
- Verify AES-256-GCM envelope/golden vectors, `rewrap`, owner/item/policy AAD, tamper and substitution denial, legacy AAD behavior and schema compatibility. No crypto/key-hierarchy rewrite before November 28.
- Verify Argon2id auth, opaque session digest persistence, expiration/logout revocation, tab-scoped bearer, generic failure behavior, cross-tenant 404/denial, POST reveal with `Cache-Control: no-store`, 15-second browser concealment and clearing on navigation/logout.
- Verify production rejects missing/demo vault keys. Document key generation, separate key/DB backup storage, retention, loss consequences and restore. Do not commit keys or expose plaintext in logs, telemetry, AI prompts, tests or docs.
- Verify trusted proxy/CORS/rate-limit configuration, XSS/session residual risk, request bounds, sanitized security events, health endpoint exposure and safe failures.
- Verify Flyway migrations and real PostgreSQL upgrade/restore, preserved envelopes and successful post-restore reveal with the **same retained key**. Refresh [THREAT_MODEL.md](THREAT_MODEL.md).

**Exit:** passing negative tests and migration/restore evidence tied to candidate SHA; no unapproved trust-boundary changes; unresolved critical risks block release.

### Phase 2 — Complete the Manager and tool journeys (P0/P1)

**Goal:** a polished usable product without AI.
- Account: register, login, logout, route protection, expired session and meaningful validation/error states.
- Tools: schema-backed policy display, secure generate, validate, generate↔validate↔rotate invariant; avoid unnecessarily transmitting real candidate passwords.
- Vault: create (manual/generated), list, metadata, edit, delete/confirmation, reveal/conceal, guided rotation, expiry timestamps and upcoming/overdue views. Test cross-user denial on every new access path.
- UX: Angular Material form/dialog/snackbar patterns with Keynest's distinct forest theme, responsive desktop/mobile, visible focus, keyboard order, accessible names, contrast, loading/empty/error states, reduced motion.
- Security UX: no durable plaintext browser storage, bounded reveal lifetime, cleanup on navigation/logout and no sensitive errors.
- Test: Angular component/browser regression **and** a human full-stack walkthrough using synthetic accounts against API + PostgreSQL.

**Exit:** sign-in → create → reload → reveal/conceal → edit/rotate → delete → sign-out works in browser, including accessibility/failure paths. Record SHA, environment and outcomes; a static Vercel preview alone is insufficient.

### Phase 3 — Deployment, operability, adoption and legal release gates (P0/P1)

**Goal:** another engineer can install, use and recover Keynest.
- Rehearse clean PostgreSQL 16 + packaged API Compose startup and separately hosted/built Angular Web; documented public API base URL, CORS, TLS/reverse-proxy, prod profile, non-demo key and health/readiness.
- Rehearse persistence, migration, backup and restore with retained vault key; preserve login and reveal after restore. Verify container non-root/read-only runtime, status-only probes, safe diagnostic exposure and HTTP smoke.
- Build independent Maven consumer on supported JDKs. Decide distribution/versioning; **do not claim publication without artifact evidence**.
- Finish README quickstart, install/runbook, architecture and API examples, release changelog, known limitations, license and private vulnerability disclosure. License choice/contact are maintainer decisions. Add contributor and compatibility notes as appropriate.
- Demo with synthetic data; verify docs/commands from a clean environment and make portfolio screenshots/walkthrough.

**Exit:** reproducible install → auth/vault journey → retained-key recovery → standalone library consumer, with working documentation and approved root `LICENSE`/`SECURITY.md`.

### Phase 4 — Optional, bounded AI-native demonstration (P2; parallel only after P0 stabilized)

**Goal:** demonstrate meaningful BYOK without creating an authorization authority.
- Retain the implemented **ephemeral BYOK policy explanation** first slice (see [ADR-0010](ADR-0010-ephemeral-byok-policy-explanation.md), [AI_BACKEND.md](AI_BACKEND.md)); do not expand to vault search or policy mutation before release.
- Provider-neutral `AiProvider` port, deterministic fake adapter, one bounded real provider adapter; explicit opt-in. Credentials are per-invocation, never persisted or included in prompts/model outputs/logs. Do not confuse provider keys with vault keys.
- Send only minimal, explicitly selected non-secret policy facts; no vault plaintext, account metadata, envelopes, keys or session bearers. Enforce fixed/allowlisted HTTPS destination, no redirects, timeout, output-size/concurrency/rate limits, no-store and safe errors.
- Treat model response as untrusted display text; deterministic policy engine and server authorization remain authoritative.
- Test disabled/invalid credentials, unauthenticated requests, provider errors, timeouts, malformed/reflected output, cancellation and redaction. With maintainer approval for data disclosure/cost, run one sanitized live-provider smoke; otherwise keep AI disabled and document deferral.

**Exit:** optional browser policy explanation has mock/negative and approved live evidence, **or** remains safely disabled with an explicit limitation. Vault operation never depends on it.

### Phase 5 — November 21 internal MVP checkpoint (P0)

**Goal:** freeze scope and gather a real evidence packet.
- Use [MVP_RELEASE_EVIDENCE.md](MVP_RELEASE_EVIDENCE.md). Record candidate SHA and CI URLs for Maven JDK 21/25, Angular build/browser tests, PostgreSQL restore, independent consumer and packaged Compose smoke.
- Perform **separate human-operated** full-stack browser and retained-key backup/restore rehearsals; CI is not a substitute.
- Verify license/disclosure, README/changelog/threat model, data privacy limitations and clean demo setup.
- Reconcile open UI and shipping issues [#25](https://github.com/JordyNicholas/Keynest/issues/25), [#26](https://github.com/JordyNicholas/Keynest/issues/26), [#31](https://github.com/JordyNicholas/Keynest/issues/31), [#32](https://github.com/JordyNicholas/Keynest/issues/32), [#33](https://github.com/JordyNicholas/Keynest/issues/33), [#42](https://github.com/JordyNicholas/Keynest/issues/42) against actual definitions of done.
- Record go/no-go. Freeze new feature scope; afterward prioritize defects, evidence, docs and packaging.

**Exit:** evidenced internal MVP or named P0 blockers with owner and resolution plan; no premature public claim.

### Phase 6 — November 22–28 stabilization and public release (P0/P1)

**Goal:** a credible open-source, portfolio-ready first release.
- Fix verified blockers with focused PRs; rerun affected tests and **all release gates on final SHA**.
- Finish changelog, version/tag, distribution artifacts (or honest local distribution notes), quickstart, synthetic demo, screenshots/walkthrough, threat/limitations disclosure.
- Verify clean deploy/recovery and full browser journeys. Verify AI-disabled core. Disable optional features lacking safe evidence.
- Maintainer approves security-sensitive merges and final claims. Publish only with P0 gates passed; otherwise document delay.

**Exit:** public tagged release, reproducible evidence, accurate feature/security claims, demo and maintainer sign-off. **Stop the November deadline review after November 28**; switch to post-release milestones.

### Phase 7 — Adoption and reliability after first release

**Goal:** turn a successful demonstration into a dependable open-source project.
- Collect user/integrator feedback; prioritize UX, accessibility, API consistency, import/export only with explicit security design, and documentation.
- Publish/version libraries and language-neutral specs, compatibility policy, release automation, SBOM/dependency/security scanning and examples as justified.
- Mature observability, redacted durable audit, multi-instance rate limiting, upgrade/backup automation and operations.
- Complete opt-in email reminders only after recipient verification/consent, safe SMTP/provider adapter, retry/idempotency and privacy tests.
- Address legacy AAD migration, CSP/session exposure, metadata privacy and security debt with ADRs/migration tests.

**Exit:** evidence-backed minor release with real external onboarding and reliable operations.

### Phase 8 — Key hierarchy, teams and portability at scale (post-release; ADR-gated)

**Goal:** support small teams and company evaluation without breaking vaults.
- Design `KeyProvider`, per-user/tenant wrapped DEKs, KEK rotation and migrations from the server-wide key; portable env/file first, cloud KMS/Vault Transit optional adapters.
- Evaluate org ownership, sharing, least-privilege roles, audit, tenant boundaries, SSO and recovery semantics. Preserve existing data and envelope compatibility.
- Add distributed operational controls, optional Helm/Terraform/cloud adapters only where deployment demand exists.

**Exit:** approved ADRs, compatibility/rollback/recovery plan, cross-tenant negative tests and portable operational evidence.

### Phase 9 — Provider-neutral AI and human-reviewed policy proposals (post-release)

**Goal:** expand AI utility without weakening deterministic policy.
- Formalize typed provider capabilities, quotas/cost bounds, privacy/retention disclosure, errors, fake adapter contract and a second provider **only after** the interface is proven.
- Consider structured policy proposals: schema validate, deterministic policy evaluation, human-readable diff and explicit human apply. The model never self-approves.
- If users request stored BYOK keys, design encrypted custody, rotation, deletion, no-readback and logging protections in a separate security-reviewed ADR; ephemeral stays default.

**Exit:** interchangeable providers and adversarial/negative tests; no model-controlled authorization or vault secret egress.

### Phase 10 — Policy-driven Agent Gateway / MCP (post-release; high risk)

**Goal:** let agents perform narrowly scoped work **without unrestricted secrets**.
- First implement authenticated **read-only, non-secret** policy/status tools with actor/resource scopes, tenant checks and sanitized audit. MCP schemas do not confer permission.
- Define PAT/OAuth identity, scope/expiry/revocation, rate limiting, replay protection, denial semantics and prompt-injection defenses in ADRs.
- Only later consider write tools: server canonical plan → human approval bound to actor/action/resource/version/expiry → one-time deterministic apply → audit; reject stale, mismatched and replayed approvals.
- Prefer server-side operation execution or short-lived constrained capability. **Never expose raw vault passwords, provider keys or unrestricted reveal/export to model context.**

**Exit:** independently reviewed least-privilege design and negative/adversarial tests, not a general autonomous vault agent.

### Phase 11 — Optional company-grade product (demand-driven)

**Goal:** mature where actual adoption warrants.
- MFA/TOTP, downloadable recovery keys, enterprise RBAC, OIDC SSO, org policy assignment, audit export, advanced sharing and incident/compliance processes.
- Optional Kubernetes/Helm/Terraform, cloud KMS adapters, high availability and operational guarantees.
- Evaluate client-side WebCrypto/WASM zero-knowledge design **only** with separate threat model and backward-compatible migration; the current server-side-open architecture is **not zero knowledge**.
- Browser extension, breach monitoring, other SDKs and parity features remain optional and require product evidence.

**Exit:** each feature has its own problem statement, approved ADR, migration/security tests, operations and support plan.

## 5. Immediate dedicated-agent queue (strict order unless evidence changes)

1. **Close governance/security docs:** ask maintainer to select license and private security reporting contact; add root `LICENSE`/`SECURITY.md`, reconcile README/THREAT_MODEL and known limitations. **Acceptance:** approved license, working private reporting route, accurate confidentiality/AI/deployment claims.
2. **Produce release evidence:** obtain exact-SHA CI and human full-stack browser + retained-key restore; validate clean Compose and independent consumer. **Acceptance:** links to passing results and dated operator walkthrough, with failures marked as failures.
3. **Fix actual P0 failures:** prioritize crypto/auth/tenant/secret exposure, migrations/recovery and broken vault journeys; include negative tests. **Acceptance:** resolved defect with targeted tests and full final-SHA release gates.
4. **Polish and package:** Material UX/accessibility, README demo, screenshots, changelog, version and artifact distribution statement. **Acceptance:** unfamiliar engineer follows docs to a successful demo.
5. **Optional AI smoke:** run only if maintainer approves provider/cost/data disclosure; otherwise leave disabled and document. **Acceptance:** no credential persistence, no vault secrets sent, bounded provider behavior, AI-disabled core unaffected.

```text
Current main / issues / accepted ADRs
  ├── P0 security + database/key recovery ────┐
  ├── P0 core browser journey ────────────────┤
  └── P0 license / security disclosure ───────┤
                                              v
                             Nov 21 internal MVP evidence
                                              |
                             P1 polish + release packaging
                             P2 optional BYOK (non-blocking)
                                              |
                             Nov 28 release go/no-go
                                              |
                Adoption → key hierarchy/teams → AI platform
                        → scoped Agent Gateway → enterprise
```

## 6. Acceptance and approval contract for every agent task

1. Inspect current `main`, open PRs/branches and issues. Reuse a focused issue with **Description / Approach / Definition of done**; normally use `KN#<issue>-slug` branch and link the issue in the PR. Avoid duplicate work with another agent.
2. Keep code PRs small; explain user impact, approach, tests, API/data/config compatibility, security/privacy, migration/rollback and residual risks. Update docs with implementation.
3. Run relevant Maven JDK 21/25, Angular, browser, PostgreSQL/Compose, independent consumer and negative security checks; link **actual results with SHA**. Never claim tests passed from configuration or test source alone.
4. **Security-sensitive changes require explicit maintainer approval of the reviewed change before merge:** crypto/envelopes, key custody, auth/session, authorization, reveal/export, AI credentials/egress, agent permissions/approvals and trust-boundary infrastructure. General authorization or green CI is not approval.
5. **Feature updates/new features also require maintainer approval; documentation-only PRs may merge without separate approval.** Keep approval discussion in chat, **not** explicit approval requests in PR bodies. Continue non-conflicting next work without waiting for new orders, but do not merge gated PRs.
6. Routine implementation uses branches/PRs, not direct `main` writes or force pushes. The maintainer authorized direct publication of this roadmap on November 15; repository branch protection still requires a PR, so publication must respect that rule. No production deployments or destructive data migrations without explicit authorization.
7. After each task report PR, commit SHA, verified evidence, risks, outstanding decisions and next dependency. Do not silently close stale issues.

**Final release evidence matrix:**

| Gate | Minimum proof |
| --- | --- |
| Security | Golden vectors, AAD tamper/substitution denial, tenant isolation, auth/session revocation, no-store bounded reveal, key config and log redaction |
| Persistence | Real PostgreSQL migration and retained-key backup/restore with post-restore login/reveal |
| Build/deploy | Exact-SHA Maven 21/25, Angular tests/build, independent consumer and packaged Compose HTTP smoke |
| UX | Human full-stack vault lifecycle, keyboard/focus, responsive/reduced-motion and failure states |
| Governance | Approved license, SECURITY.md, threat/limitations, README/changelog and distribution truthfulness |
| AI if enabled | Fake-provider negative tests, egress/credential redaction, approved sanitized live-provider smoke, AI-disabled regression |
| Release | Final SHA, CI URLs, manual rehearsal, known limitations, maintainer go/no-go and tag/notes |

## 7. Honest limitations and maintainer decisions

**Must disclose at first release:** server-side decrypt and a server-wide key; plaintext metadata; JS-readable session bearer/XSS risk; per-JVM rate limits; non-durable security events; legacy AAD behavior where applicable; web separate from Compose; console-only reminder simulation; no independent audit/zero-knowledge claim; third-party processing when BYOK AI is enabled.

**Decisions reserved for maintainer:** license; private security-reporting address; release version/distribution; optional live BYOK model/cost/privacy approval; final public-release go/no-go; later ADRs for per-user key custody, team sharing and agent identity/approvals.

## 8. Document hierarchy and upkeep

- **This file:** canonical end-to-end sequencing, release scope and agent execution priorities.
- [DELIVERY_PLAN.md](DELIVERY_PLAN.md): original overlapping November schedule and issue map; historical dates do not override this file.
- [MVP_RELEASE_EVIDENCE.md](MVP_RELEASE_EVIDENCE.md): live Nov 21 candidate checklist and evidence links; update it as work progresses.
- [ROADMAP.md](ROADMAP.md): older technical breakdown, with historical Angular/version assumptions; reconcile against current code before using.
- [PROJECT_CHARTER.md](PROJECT_CHARTER.md) / [PRODUCT.md](PRODUCT.md): mission and product requirements.
- [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md) / [AI_BACKEND.md](AI_BACKEND.md) / [ADR-0010](ADR-0010-ephemeral-byok-policy-explanation.md): proposed AI evolution and implemented narrow contract.
- [THREAT_MODEL.md](THREAT_MODEL.md) and accepted ADRs: technical security authority.
- [AGENTS.md](../AGENTS.md) / [PROJECT_PR_WORKFLOW.md](PROJECT_PR_WORKFLOW.md): branch, review, approval and coordination rules.

**Maintenance:** update this master roadmap only for scope/phase changes; use issues, PRs and the evidence checklist for daily progress. Never rewrite the historical engineering baseline to imply later work existed at its pinned SHA.
