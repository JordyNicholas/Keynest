# Keynest — Canonical Product & Engineering Roadmap

**Status:** Active, maintainer-directed execution plan
**Updated:** November 28, 2026 (America/Sao_Paulo)
**Implementation-complete internal MVP checkpoint:** **Saturday, November 21, 2026**
**Portfolio-ready open-source release target:** **Saturday, November 28, 2026**
**After November 28:** staged follow-on releases; no invented delivery dates
**Release status (November 28):** v0.1.0 and v0.1.1 are published tagged source releases ([0.1.0](../../releases/0.1.0.md), [0.1.1](../../releases/0.1.1.md)). M1 has proceeded through separately approved slices; the remaining gates and proposed later milestones are recorded in §4A/§5. No new release or delivery date follows from this reconciliation.

> **Dedicated agent: start here.** This is the authoritative **scope, sequence, dependency and acceptance-gate** roadmap for Keynest. It consolidates prior product discussions, the accepted charter and the older phased plans. It does **not** certify features as complete. Read [AGENTS.md](../../../AGENTS.md), [PROJECT_CHARTER.md](charter.md), [PRODUCT.md](product.md), accepted ADRs, [THREAT_MODEL.md](../../security/threat-model.md), [AI_ARCHITECTURE.md](../../design/ai-and-agents.md), [DELIVERY_PLAN.md](../../archive/delivery-plan.md), [MVP_RELEASE_EVIDENCE.md](../../archive/mvp-release-evidence.md), and [PROJECT_PR_WORKFLOW.md](../pull-requests.md). Where old dates or feature scope disagree, this roadmap governs **execution sequencing**; accepted ADRs and explicit maintainer decisions govern technical/security contracts.

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

At the November 15 inspection, `main` pointed to [`6d53654`](https://github.com/JordyNicholas/Keynest/commit/6d53654bba068b59bbe238aefe9e7aff2e70bc15). Source and docs include auth/vault security hardening, Flyway/PostgreSQL, container checks, independent library example, Angular vault/rotation/expiry features, optional ephemeral BYOK explanation backend and UI, and reminder outbox/console simulation. This is **observed implementation**, not a claim that every journey passed. [PR #119 CI](https://github.com/JordyNicholas/Keynest/actions/runs/43629011117) is recorded as a successful baseline in the [MVP evidence](../../archive/mvp-release-evidence.md); collect **new exact-SHA evidence** for the release candidate.

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

**Security cannot be waived for a date.** November 21 is an **internal implementation-complete MVP checkpoint**, not a release-candidate or full-system testing milestone; comprehensive verification follows on November 22–26, and release-candidate preparation follows on November 27–28; November 28 is the public-release target **only if P0 gates pass**. If they do not, keep the candidate internal, document the blocker and make a maintainer go/no-go decision.

## 4. Execution roadmap, from foundation to mature product

### Phase 0 — Foundation and verified baseline (historical; repeat before each task)

**Goal:** one shared engineering source of truth.
- Inspect latest `main`, open PRs, branches, issues, relevant accepted ADRs and CI before changing files. Avoid overlapping another agent's work.
- Preserve Java 21/Spring Boot 4/Angular 21/PostgreSQL, independent libraries, clean module boundaries and versioned contracts.
- Keep [ENGINEERING_BASELINE.md](../../archive/engineering-baseline.md) pinned to its original SHA; do not rewrite history. Add ADRs for consequential changes.
- Confirm the task's issue, dependency, scope, rollback and test plan before implementing.
**Exit:** current SHA and affected contracts identified; no conflicting work; issue and acceptance criteria defined.

### Phase 1 — Crypto, auth, key custody and data integrity (P0; harden and verify)

**Goal:** a defensible secrets-management trust boundary.
- Verify AES-256-GCM envelope/golden vectors, `rewrap`, owner/item/policy AAD, tamper and substitution denial, legacy AAD behavior and schema compatibility. No crypto/key-hierarchy rewrite before November 28.
- Verify Argon2id auth, opaque session digest persistence, expiration/logout revocation, tab-scoped bearer, generic failure behavior, cross-tenant 404/denial, POST reveal with `Cache-Control: no-store`, 15-second browser concealment and clearing on navigation/logout.
- Verify production rejects missing/demo vault keys. Document key generation, separate key/DB backup storage, retention, loss consequences and restore. Do not commit keys or expose plaintext in logs, telemetry, AI prompts, tests or docs.
- Verify trusted proxy/CORS/rate-limit configuration, XSS/session residual risk, request bounds, sanitized security events, health endpoint exposure and safe failures.
- Verify Flyway migrations and real PostgreSQL upgrade/restore, preserved envelopes and successful post-restore reveal with the **same retained key**. Refresh [THREAT_MODEL.md](../../security/threat-model.md).
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
- Retain the implemented **ephemeral BYOK policy explanation** first slice (see [ADR-0010](../../architecture/decisions/ADR-0010-ephemeral-byok-policy-explanation.md), [AI_BACKEND.md](../../api/policy-explanations.md)); do not expand to vault search or policy mutation before release.
- Provider-neutral `AiProvider` port, deterministic fake adapter, one bounded real provider adapter; explicit opt-in. Credentials are per-invocation, never persisted or included in prompts/model outputs/logs. Do not confuse provider keys with vault keys.
- Send only minimal, explicitly selected non-secret policy facts; no vault plaintext, account metadata, envelopes, keys or session bearers. Enforce fixed/allowlisted HTTPS destination, no redirects, timeout, output-size/concurrency/rate limits, no-store and safe errors.
- Treat model response as untrusted display text; deterministic policy engine and server authorization remain authoritative.
- Test disabled/invalid credentials, unauthenticated requests, provider errors, timeouts, malformed/reflected output, cancellation and redaction. With maintainer approval for data disclosure/cost, run one sanitized live-provider smoke; otherwise keep AI disabled and document deferral.
**Exit:** optional browser policy explanation has mock/negative and approved live evidence, **or** remains safely disabled with an explicit limitation. Vault operation never depends on it.

### Phase 5 — November 16–21: complete MVP implementation (P0/P1)

*Status (November 25): historical — superseded by the published v0.1.0/v0.1.1 releases; retained for traceability.*

**Goal:** finish the planned product before full release rehearsals. The November 21 checkpoint means **implementation complete**, not fully tested or publicly ready.
- Reconcile current `main`, open PRs and issue definitions of done before starting work; do not duplicate another agent's changes.
- Finish Angular/Material account, tools and vault UX: register/login/logout, list/create/edit/delete, bounded reveal/conceal, generate/validate, guided rotate, expiry views, responsive/accessibility fundamentals, error and empty states.
- Finish the associated Spring Boot/API integration, preserving crypto, auth, ownership, migration and key-custody boundaries. Fix critical security/correctness defects as soon as discovered.
- Keep **targeted unit/integration/negative security tests and PR CI** mandatory during implementation; repair failing PR checks before merge. Comprehensive human browser, retained-key restore and deployment rehearsals are scheduled **after** implementation.
- Complete only the existing narrow, optional BYOK policy explanation if core delivery is not threatened; AI stays disabled by default and cannot authorize actions or access vault secrets.
- Reconcile open UI issues [#25](https://github.com/JordyNicholas/Keynest/issues/25), [#26](https://github.com/JordyNicholas/Keynest/issues/26), [#31](https://github.com/JordyNicholas/Keynest/issues/31), [#32](https://github.com/JordyNicholas/Keynest/issues/32), [#33](https://github.com/JordyNicholas/Keynest/issues/33) with implementation. Do not assume open issues prove missing functionality.
**Exit:** release-scoped functionality implemented on `main`, affected PR checks passing, and an explicit list of any unfinished functionality or critical defects. No release-candidate declaration.

### Phase 6 — November 22–26: full-system verification and defect remediation (P0/P1)

*Status (November 25): historical — evidence lives in [MVP_RELEASE_EVIDENCE.md](../../archive/mvp-release-evidence.md); unclaimed checks stay unclaimed.*

**Goal:** test the implemented product end to end, then fix defects without adding scope.
- Perform a **human-operated** full-stack browser walkthrough with synthetic accounts: login, create, reload, reveal/conceal, edit/rotate, delete, logout, keyboard/focus, responsive and failure paths.
- Rehearse clean PostgreSQL/API Compose and separately hosted/built Angular setup, migrations, backup/restore with the **same separately retained vault key**, and post-restore login/reveal.
- Review golden vectors, AAD tamper/tenant denial, session revocation, key guards, no-store reveal, redaction and deployment settings. Verify core operation with AI disabled.
- Record actual SHA, environment, commands/results, failures and fixes in [MVP_RELEASE_EVIDENCE.md](../../archive/mvp-release-evidence.md). A successful CI configuration, PR or Vercel status is not substitute evidence.
- Optional live-provider BYOK smoke requires maintainer-approved credential, cost and data-disclosure decisions. Disable/defer AI if it endangers P0.
**Exit:** verified complete journeys and recovery, critical defects resolved, and documented limitations. If critical defects persist, defer release candidacy.

### Phase 6B — November 27–28: release candidate, packaging and public go/no-go (P0/P1)

*Status (November 25): historical — release GO recorded November 23; see the release notes linked above.*

**Goal:** publish a credible, demonstrable open-source release only after verification.
- Freeze a candidate SHA and obtain **actual passing CI on that exact SHA** for Maven 21/25, Angular tests/build, independent consumer, packaged Compose and required security/regression gates.
- Finalize README/quickstart, changelog, threat and known-limitations disclosures, license/SECURITY.md, version/tag, honest distribution notes, synthetic demo and portfolio screenshots/walkthrough.
- Link browser, recovery and deployment evidence; confirm AI-disabled core and disable optional capabilities without safe evidence.
- Obtain maintainer go/no-go and required security/feature approvals. If any P0 gate fails, delay public release rather than weakening the gate.
**Exit:** public tagged release with evidence and maintainer sign-off, or explicit no-go. Stop the November deadline review after November 28.

### Phase 7 — Adoption and reliability after first release

**Completed first slice — November 24 (#256/#266):** source-SHA-bound Java/Web inventories and known-vulnerability matching shipped in [0.1.1](https://github.com/JordyNicholas/Keynest/releases/tag/v0.1.1) with approved dependency remediation. Final source `b3137c770349744a084202263b504a0ed6dc056a` passed all seven [application checks](https://github.com/JordyNicholas/Keynest/actions/runs/45388523779) and [inventory/OSV matching](https://github.com/JordyNicholas/Keynest/actions/runs/45388524003): Java151/Web959, no advisory matches or suppressions, unavailable matcher correctly error127. Source inventories do not establish complete safety or an image scan. Follow-up CI hardening (#269/#271) and source-release automation (#270/#272) subsequently merged through separately approved PRs. Registry publication, public hosting and third-party migration remain deferred. See [dependency security](../../security/dependency-scanning.md).

**Goal:** turn a successful demonstration into a dependable open-source project.
- Collect user/integrator feedback; prioritize UX, accessibility, API consistency, import/export only with explicit security design, and documentation.
- Publish/version libraries and language-neutral specs, compatibility policy, release automation, SBOM/dependency/security scanning and examples as justified.
- Mature observability, redacted durable audit, multi-instance rate limiting, upgrade/backup automation and operations.
- Complete opt-in email reminders only after recipient verification/consent, safe SMTP/provider adapter, retry/idempotency and privacy tests.
- Address legacy AAD migration, CSP/session exposure, metadata privacy and security debt with ADRs/migration tests.
**Hardening slice (M1, initial proposal November 25; reconciled November 28).** The original H1–H8 problem statements below describe the source-archive review that started this slice; they are not current missing-control claims. Current implementation and remaining scope are mapped here. Items marked ◆ retain explicit review requirements (§6).

| Item | Current implementation / remaining work |
| --- | --- |
| H1 | API/Web headers and four origin/motion browser paths merged through #281/#283/#285/#293/#295; focused transport design #279/#331 accepted. Operator ingress checker #333/#334 is merged. Actual ingress, cookie/CSRF migration and T02 acceptance remain open under #278. |
| H2 | Dummy verification and normalized-identifier throttling merged through #297/#299. Verified-email design #300/#335 is accepted; #336/#337 merged eligibility/direct-signup retirement; #339/#340 merged bounded internal verification state; #341 is the TLS SMTP/mailbox-validation candidate under remaining activation work #338; process-local availability/distributed limits remain in T01. |
| H3 | Central default-deny API boundary and route inventory merged through #304. New transports still need separate review. |
| H4 | Bounded expiry cleanup #306, owner-wide revocation #309 and password change/session replacement #310 merged; ADR-0017/0018 define the limits. |
| H5 | Whole-candidate Fisher–Yates placement and regression tests merged through #312; no uniform-over-all-valid-passwords claim. |
| H6 | Saturation/fail-closed availability boundaries documented through #314. Shared admission and operational verification remain; documentation is not residual-risk acceptance. |
| H7 | Architecture #316, measured per-module coverage floors #318, incremental formatting #320 and source-reviewed static analysis #322 merged. These gates do not replace a security audit. |
| H8 | Public onboarding and task-oriented documentation merged through #324/#326. An actual outside-integrator onboarding result remains unclaimed. |

The integrated baseline `b8dc13974b0c9e3f7b1aa745acede65f59cdd76f` passed all seven [application checks](https://github.com/JordyNicholas/Keynest/actions/runs/46096956680) and [dependency matching](https://github.com/JordyNicholas/Keynest/actions/runs/46096956684), including all four browser origin/motion lifecycles. #328 retains intermittent CI history and a later maintainer-reported successful manual walkthrough with unspecified source/environment. Neither result accepts the remaining H1/M1 risks.

**Original review scope (historical problem statements):**
- **H1 ◆ CSP and security headers (already tracked: T02, ADR-0007).** Promote from "before public Internet deployment" to the first M1 item. Define and test a Content-Security-Policy for the Angular app (Vercel configuration and any reverse-proxy sample) and baseline API response headers; document HSTS in [DEPLOYMENT.md](../../operators/deployment.md). Reassess HttpOnly-cookie transport with CSRF/CORS as an ADR-0007 amendment.
- **H2 ◆ Sign-in hardening (extends T01).** `AuthService.login` returns for an unknown email before any password verification, so response time can distinguish registered addresses; add an equal-cost dummy verification. The limiter counts by route and resolved client, not by target account; add per-account failure throttling/backoff. Decide and document registration enumeration semantics.
- **H3 ◆ Central default-deny authentication.** Controllers currently call `requireUser` individually. Introduce one filter/interceptor (or Spring Security) with an explicit public-route allowlist and a route-inventory test that fails when a new route has no authentication decision. Prerequisite for Phase 10.
- **H4 ◆ Session lifecycle (extends T03).** Scheduled expired-session cleanup, revoke-all-sessions, and change-master-password with revocation of other sessions. Recovery semantics stay unchanged unless an ADR says otherwise.
- **H5 ◆ Generator category placement.** `PasswordGenerator.ensureCategories` places required categories at fixed leading positions, slightly reducing entropy. Use uniform placement (for example Fisher–Yates) and add a statistical position-uniformity test. The browser generator already uses rejection sampling.
- **H6 Availability controls (extends T09).** Hashing-capacity saturation returns 503 to all callers and a full limiter table fails closed; record both as threat-model entries and document edge/shared limiting.
- **H7 Quality gates in CI (relates #269).** Keep the existing seven checks and add formatter enforcement (Spotless), static analysis (SpotBugs or Error Prone), coverage reporting with a floor (JaCoCo) and architecture tests (ArchUnit). Consider separating export/restore from the `vault` package once boundaries are testable.
- **H8 Public onboarding path.** Separate a short public path (README quickstart, security model, limitations) from agent/process documents so external integrators are not routed through internal workflow text.
**Exit:** evidence-backed minor release with real external onboarding and reliable operations. **M1 exit additionally requires** H1–H7 each fixed with negative tests or explicitly accepted in [THREAT_MODEL.md](../../security/threat-model.md).

### Phase 8 — Key hierarchy, teams and portability at scale (post-release; ADR-gated)

**Goal:** support small teams and company evaluation without breaking vaults.
- Design `KeyProvider`, per-user/tenant wrapped DEKs, KEK rotation and migrations from the server-wide key; portable env/file first, cloud KMS/Vault Transit optional adapters.
- Evaluate org ownership, sharing, least-privilege roles, audit, tenant boundaries, SSO and recovery semantics. Preserve existing data and envelope compatibility.
- Add distributed operational controls, optional Helm/Terraform/cloud adapters only where deployment demand exists.
**Post-release additions (proposed November 25):**
- **Key resolution gap.** Envelopes already record a key id, but `VaultService` loads a single configured key and does not resolve by id, and `rewrap` in `keynest-seal` is not used by the Manager. The design must add keyring lookup by key id, an operator rotation command and a **rotation drill** (back up, rotate, restore, reveal) with rollback.
- **Nonce volume as a design input.** With random 96-bit GCM nonces, NIST SP 800-38D bounds encryptions per key at 2^32. Rotation thresholds or per-user/per-item keys keep Keynest far inside that bound; record the chosen threshold in the ADR.
- **Seal v2 candidate.** Seal v1 does not authenticate the header key id (documented in 0.1.1). Evaluate authenticating header fields in a v2 envelope with dual-read compatibility and golden vectors.
- **Be explicit about the trust model.** Until a separate zero-knowledge ADR is accepted, describe the Manager as server-managed, not zero knowledge.
**Exit:** approved ADRs, compatibility/rollback/recovery plan, cross-tenant negative tests and portable operational evidence.

### Phase 9 — Provider-neutral AI and human-reviewed policy proposals (post-release)

**Goal:** expand AI utility without weakening deterministic policy.
- Formalize typed provider capabilities, quotas/cost bounds, privacy/retention disclosure, errors, fake adapter contract and a second provider **only after** the interface is proven.
- Consider structured policy proposals: schema validate, deterministic policy evaluation, human-readable diff and explicit human apply. The model never self-approves.
- If users request stored BYOK keys, design encrypted custody, rotation, deletion, no-readback and logging protections in a separate security-reviewed ADR; ephemeral stays default.
**Post-release additions (proposed November 25):**
- **First proposal slice (Track B, M2).** Natural-language text or pasted site password rules produce a candidate policy that is schema-validated, evaluated deterministically, shown as a human-readable diff and applied only by explicit human action. Disabled by default and behind a flag until its ADR is accepted.
- **AI egress path ADR.** The current explanation sends only non-secret built-in policy facts, so the server proxy mainly adds guardrails while exposing the user's provider key to the operator in transit. The browser-direct versus server-proxied trade-off is now recorded in accepted [ADR-0019](../../architecture/decisions/ADR-0019-ai-egress-boundaries.md); the implemented explanation still follows ADR-0010 until separately reviewed changes land.
- **Provider breadth.** An OpenAI-compatible or local-model adapter for self-hosters requires redesigning the fixed-destination allowlist (SSRF guard) around explicit operator configuration. Needs its own ADR before code.
- **Metadata boundary.** Do not send vault labels, URLs or usernames to any provider without an ADR covering explicit selection and redaction (T07).
**Exit:** interchangeable providers and adversarial/negative tests; no model-controlled authorization or vault secret egress.

### Phase 10 — Policy-driven Agent Gateway / MCP (post-release; high risk)

**Goal:** let agents perform narrowly scoped work **without unrestricted secrets**.
- First implement authenticated **read-only, non-secret** policy/status tools with actor/resource scopes, tenant checks and sanitized audit. MCP schemas do not confer permission.
- Define PAT/OAuth identity, scope/expiry/revocation, rate limiting, replay protection, denial semantics and prompt-injection defenses in ADRs.
- Only later consider write tools: server canonical plan → human approval bound to actor/action/resource/version/expiry → one-time deterministic apply → audit; reject stale, mismatched and replayed approvals.
- Prefer server-side operation execution or short-lived constrained capability. **Never expose raw vault passwords, provider keys or unrestricted reveal/export to model context.**
**Post-release additions (proposed November 25):**
- **ADR first, code later.** Accepted [ADR-0020](../../architecture/decisions/ADR-0020-agent-trust-boundaries.md) records the M1 design boundary for identity, scopes, approval binding, replay, denial and audit. Agent runtime and later leases remain unimplemented; no capability is enabled by default before its Track A prerequisites are verified (§4A).
- **Differentiation hypotheses to validate, not claim.** (a) **Leases, "use, never read":** short-lived, scoped, single-purpose; the server performs the operation or injects the credential and no response carries the secret; sensitive leases require human approval bound to actor/action/resource/version/expiry. (b) **Canary credentials:** honeytoken items whose use raises sanitized security events. (c) **Burn-after-use:** rotate secrets touched by a lease or session after it ends; rotation is a write and follows the same approval design. (d) **Redaction guard:** tool output, logs and errors are tested never to contain a leased value.
- **Tool design rule.** No agent tool accepts or returns a real password or secret. Generation and validation of real candidate passwords are not exposed as agent tools; read-only tools return non-secret metadata only.
- **Standards and market context (verify before citing).** Bitwarden has published an MCP server and an agent-access SDK ([MCP server](https://bitwarden.com/en-gb/blog/bitwarden-mcp-server/), [agentic credentials overview](https://bitwarden.com/de-de/blog/how-bitwarden-helps-secure-agentic-ai-access-to-your-credentials/)), and 1Password has stated it will not expose raw credentials over MCP ([where MCP fits](https://1password.com/blog/where-mcp-fits-and-where-it-doesnt)). Spike on alignment with existing open standards before defining a Keynest-specific protocol, and do not claim uniqueness.
- **Evidence per hypothesis.** Problem statement, adversarial test plan and integrator feedback before any hypothesis becomes default-on.
**Exit:** independently reviewed least-privilege design and negative/adversarial tests, not a general autonomous vault agent.

### Phase 11 — Optional company-grade product (demand-driven)

**Goal:** mature where actual adoption warrants.
- MFA/TOTP and downloadable recovery keys (M2 may promote MFA/TOTP ahead of demand; see §7), enterprise RBAC, OIDC SSO, org policy assignment, audit export, advanced sharing and incident/compliance processes. Account password change is already delivered under H4/ADR-0018.
- Optional Kubernetes/Helm/Terraform, cloud KMS adapters, high availability and operational guarantees.
- Evaluate client-side WebCrypto/WASM zero-knowledge design **only** with separate threat model and backward-compatible migration; the current server-side-open architecture is **not zero knowledge**.
- Browser extension, breach monitoring, other SDKs and parity features remain optional and require product evidence.
**Exit:** each feature has its own problem statement, approved ADR, migration/security tests, operations and support plan.

## 4A. Post-release operating model and milestones

**Status:** M1 execution has progressed through separately approved slices, reconciled November 28 above. Later milestone scope/decisions remain proposed (§7). This section sets sequence, gates and capacity rules only. It contains no delivery dates, enables no capability by inference and changes neither the §1 invariants nor the §6 approval contract. Phases 7–11 hold the detailed scope; milestones M1–M4 group them into an order.

**Goal.** Make Keynest a dependable open-source option for developers, integrators and small teams while investing in differentiated AI-era capability, **without letting innovation outrun the trust boundary**.

**Two tracks, one precedence rule: foundation gates innovation.**
- **Track A — Foundation:** trust boundary, key custody, account lifecycle, operations, quality gates, governance, library distribution.
- **Track B — Differentiation:** human-reviewed AI policy tooling and agent-safe credential use (Phases 9–10).
- Track B may be **designed, prototyped behind a disabled-by-default flag and recorded in ADRs at any time**. It may not be enabled by default, described as shipped or merged default-on until the Track A prerequisites listed for it below are **verified (SHA + link)**.
- Capacity guidance: about 60% Track A and 40% Track B through M2, about 50/50 from M3. If capacity is short, cut Track B scope first; never relax a Track A gate.
- Every Track B capability keeps §1: deterministic backend authority, AI optional, no raw vault secret, provider key or unrestricted reveal/export in model context, and no model-driven authorization.
**Why this order.** Agent-facing features multiply the consequence of a stolen session, a weak sign-in path or a single server-wide key. M1 and M2 therefore close the sign-in, session, authentication-structure and key-custody gaps before any agent capability is enabled.

### M1 — Hardening
- **Track A:** complete the remaining Phase 7 H1–H7 gates/acceptance. CI/source-release automation #269/#270 is merged; #268 remains supplementary platform/upgrade evidence, not a retroactive release failure.
- **Track B (design only):** AI egress ADR-0019 and agent trust ADR-0020 are accepted through #327/#330; the standards spike is recorded. Later implementation/enablement still requires the listed foundation gates and focused review.
- **Exit:** H1–H7 fixed with negative tests or explicitly accepted in the threat model; CI quality gates active; both ADRs drafted and reviewed.
### M2 — Trust
- **Track A:** Phase 8 first slice (keyring resolution by key id, rotation command, rotation drill, migration from the server-wide key with rollback); durable redacted audit and shared rate limiting; MFA/TOTP if promoted (§7); library publication and compatibility-policy decision. Account password change is already delivered under H4.
- **Track B:** Phase 9 first slice (human-reviewed policy proposals) behind a flag; a second provider only after the port is proven.
- **Prerequisites for Track B enablement:** M1 exit and the accepted AI egress ADR.
- **Exit:** rotation-drill evidence tied to an exact SHA; cross-tenant negative tests; adversarial tests for the proposal flow.
### M3 — Agent beta (flagged)
- **Track A:** independent security review commissioned before any agent capability is enabled by default (scope and budget are maintainer decisions); load and saturation test with recorded limits.
- **Track B:** Phase 10 in order: read-only non-secret metadata tools; short-lived scoped leases with human approval; redaction guard; canary credentials and burn-after-use rotation.
- **Prerequisites:** H3 central authentication, M2 keyring and the accepted agent trust-model ADR.
- **Exit:** adversarial tests show no tool output, log, lease record or error contains a vault secret; review findings fixed or explicitly accepted.
### M4 — 1.0 candidate
- Close review findings; publish upgrade and rollback runbooks; state compatibility guarantees for the Seal envelope and policy schema; refresh limitations. Agent features leave beta only with the evidence above.
**Proposed definition of "solid" for a 1.0 candidate.**
- Every High and Medium threat-model item is fixed or explicitly accepted in an ADR.
- Key rotation, backup/restore and session revocation each have a recorded drill.
- CI enforces format, static analysis, coverage floor, architecture rules and dependency matching.
- An external review is complete with findings addressed.
- A new integrator reaches a working seal/policy/generate call from the README in about half an hour, verified by an outside tester.
**Success measures (proposed).** Time to first successful integration; number of external consumers; share of agent actions completed without any plaintext secret in model context.

## 5. Immediate dedicated-agent queue (implementation first)

**Current continuation — November 28:** the maintainer requested roadmap review and continued work after reporting no visible problem in the focused #328 manual walkthrough. The approved #279/#332/#289/#290 batch is merged; the integrated baseline above is green. Preserve #328 as a CI-reliability investigation; its cause is not established and no application change follows from a hypothesis.

1. **Finish H1 through focused slices (#278):** the opt-in [HTTPS ingress contract checker](../../operators/https-ingress.md) is merged through #333/#334; collect actual operator evidence when a deployment is in scope. The accepted [cookie/CSRF design boundary](../../design/browser-transport/ADR_0007_REASSESSMENT.md) still needs a reviewed implementation/amendment and isolated HTTPS lifecycle/negative/migration tests. Local fixture passes do not certify ingress, accept T02 or close M1. Public hosting remains deferred.
2. **Continue approved account-lifecycle design:** #300/#335's [accepted design ADR-0021](../../design/account-lifecycle/ADR-0021-verified-email-activation.md) defines signup, delivery, abuse limits, legacy verification and rollout. The first runtime slice #336/#337 is merged: eligibility/upgrade state preserves old-owner access and direct signup is retired while mail activation is built. The approved direction remains mandatory new-account verification with preserved existing-owner password sign-in/Vault access and a verification prompt; the detailed journey/API/defaults were accepted as design in #335; implementation evidence is still required. #302's approved five-minute, single-use action code adds confirmation without replacing fresh password proof. #301 permits pursuing optional operator-configured OIDC design; provider selection, account-linking/fresh-authentication details and milestone promotion remain review decisions. Bounded challenge/queue state merged through #339/#340. The next candidate, #341, adds mailbox validation and an internal bounded TLS SMTP adapter under #338; it enables no automatic worker or public activation endpoint. Mail delivery, public activation, legacy confirmation, UI and restore invalidation remain separate reviewed work; federation is not enabled.
3. **Keep M2 dependency-ordered:** key-ID resolution/keyring/rotation and rollback drill, durable redacted audit, shared limiting, library distribution/compatibility and human-reviewed AI policy proposals remain future work. Begin detailed designs without claiming M1 exit or promoting runtime capabilities. MFA, provider/model/cost, external review, registry publication, hosting and third-party migration retain their explicit decisions/deferred status.
4. **Record supplementary evidence accurately (#268):** preserve historical SHA/environment reports. The latest brief manual report does not supply missing Windows/Edge, upgrade, cross-instance or post-commit lost-response evidence.

**Initial post-release direction — November 25 (historical):** the original proposal began with M1 Track A hardening and design-only AI/agent work. Use the current status/queue above instead of reimplementing already merged slices. New security-sensitive and feature PRs still require explicit approval of the reviewed change before merge (§6).

**Active post-release queue — November 24:** 0.1.1 is published; version preparation #267 and exact-source gates are complete. Reconcile the board without relabelling historical tests: the shipped homepage/overview scope #199 is closed on the maintainer's accepted Chrome/Ubuntu release report; remaining upgrade/platform/cross-instance/lost-response verification is #268 and is not claimed as passed. Obsolete rename checklist #44 is closed with no rename performed; #154 remains open only for deferred third-party migration. Next, review #271 / #269 (pinned actions, no persisted checkout credentials, weekly dependency matching) and #272 / #270 (dry-run source bundles, same-SHA main verification and explicitly owner-dispatched publication). These new security-sensitive workflows are candidates, not approved/merged capabilities. Keep publication off in rehearsals and require chat approval of each reviewed change before merge. No new public release is needed merely to test the automation. After this reliability slice, scope the next product milestone separately; live BYOK still requires an explicit provider/model, privacy and cost decision. Do not enable AI, registry publication, hosting, migration destinations or future key/session/agent architecture by inference.

**Approved maintenance release — November 24 (#266):** dependency patches #260/#261 and additional scoped development remediation #263 are merged with explicit maintainer approval; source inventory/OSV automation #257 is also approved and merged. The broader review and Seal v1 key-ID documentation clarification merged through #265. Prepare 0.1.1 version/source notes, rerun all seven application checks and source-bound dependency matching on the final merged revision, then publish a new immutable tag/release. Preserve v0.1.0 and historic manual evidence. No hosting, registry publication, AI enablement or third-party migration is included. See [maintenance release notes](../../releases/0.1.1.md) and [risk review](../../archive/security-reviews/maintenance-review-2026-11-24.md).

**Approved release packaging — November 23:** following release GO, the maintainer approved preparing version 0.1.0, verifying the source package, publishing the tagged GitHub source release and reconciling completed tracking (#254). Keep exact-SHA CI, historical rc.1 provenance and documented limitations. Third-party migration, public hosting, registry publication and AI expansion remain deferred; do not enable them as part of packaging. See [the release notes](../../releases/0.1.0.md).

**Maintainer verification and scope — November 23:** [the full report](../../archive/mvp-release-evidence.md#november-23-maintainer-verification--native-restore-and-core-release-paths) records passing Chrome/Ubuntu runtime, browser/accessibility, AI-disabled, HTTP/CORS and separate-database retained-key recovery checks on `fda972f070af68e6d957f3c0d88e29181d23307b`, with exact environment, limitations and [green same-SHA CI](https://github.com/JordyNicholas/Keynest/actions/runs/45213003321). Native export/restore and reviewed duplicates are merged through #249 / accepted ADR-0016. The maintainer subsequently clarified that **Update existing, Keep both and ambiguous-target selection all passed** in that run and explicitly selected **release GO** on November 23. Windows/Edge, a separate cross-instance native browser-file round trip and lost-response-after-commit behavior remain unclaimed. **Third-party migration is deferred and is not a release prerequisite.** The pinned Bitwarden harness merged through #247 but does not verify destination save/sync or enable downloads. Prioritize release-note/evidence updates and final version/artifact provenance with passing exact-SHA CI. The maintainer's go/no-go is GO; it does not waive security gates or expand verified scope. Public hosting remains deferred; older queue entries below are historical and do not override this direction.


**Maintainer validation follow-up — November 22:** the requested account/reference polish (#220), consolidated tools and inline Account diagnostics (#221), and semantic Vault controls (#222) are merged through maintainer-approved PRs #223, #224 and #225. The maintainer is revalidating the interface; keep #199 and the release gates open for actual results. Preserve account policy, secret cleanup, authorization and reduced motion. Continue portability implementation through separate reviewed PRs while this walkthrough proceeds.

**Maintainer direction — November 22, Vault portability:** the agreed plan in [#154](https://github.com/JordyNicholas/Keynest/issues/154) uses destination-first migration exports, versioned native JSON, an authenticated encrypted backup container, and destination-specific JSON/CSV only after importer verification. The native format foundation is merged in [#217](https://github.com/JordyNicholas/Keynest/pull/217). The maintainer has explicitly approved the encrypted file codec in [#219](https://github.com/JordyNicholas/Keynest/pull/219) and [ADR-0012](../../architecture/decisions/ADR-0012-portable-backup-container.md); it does not implement an HTTP download or database restoration. The container is merged through #219. Authenticated native preview/download (#226) and the Vault workflow (#230) are merged through explicitly approved PRs #227/#231 ([API contract](../../api/vault-export.md), [accepted ADR-0013](../../architecture/decisions/ADR-0013-authenticated-vault-export.md)). The internal receiving-database restore foundation (#234) is merged through maintainer-approved [#235](https://github.com/JordyNicholas/Keynest/pull/235), including a verified PostgreSQL cross-database/key/restart test. Authenticated single-use preview/apply transport is merged through maintainer-approved [#241](https://github.com/JordyNicholas/Keynest/pull/241) / [accepted ADR-0015](../../architecture/decisions/ADR-0015-authenticated-native-restore.md), including packaged/hosted PostgreSQL/API round trips. Browser restoration is merged through maintainer-approved #245 (#244); see [the web contract and user guide](../../manager/backup-and-restore.md). Pinned destination importer verification under #246 merged through #247 / [the interoperability record](../../archive/bitwarden-interoperability.md). Further migration work is deferred by the November 23 direction above; keep unverified migration formats unavailable. Retain separate PRs, CI and chat approval for subsequent reviewed feature/security changes. This work does not imply a completed Windows/Edge walkthrough or release gate.

**November 20 validation follow-up:** the maintainer reported passing browser, accessibility, runtime and retained-key recovery checks on `ac03ce592db56ca52719dcaef00b2f6abe7154a1`, with policy consistency and UX defects. The fixes for [#192](https://github.com/JordyNicholas/Keynest/issues/192) and the selected shared account policy in [#193](https://github.com/JordyNicholas/Keynest/issues/193) are merged through maintainer-approved [#194](https://github.com/JordyNicholas/Keynest/pull/194) and [#195](https://github.com/JordyNicholas/Keynest/pull/195). Prioritize their targeted Windows/Edge and V6 recovery rechecks before AI expansion. See [the rehearsal record](../../archive/mvp-release-evidence.md#november-20-maintainer-rehearsal). Implementation is merged; the affected human checks remain open before release gates close. See [the post-merge checkpoint](../../archive/mvp-release-evidence.md#november-20-policy-and-ux-fix-checkpoint).


**Maintainer direction — November 20, tailored UX:** prioritize a complete, reviewed public homepage and signed-in account overview without rushing this feature. The temporary/dismissible shared-error refinement is merged in [#198](https://github.com/JordyNicholas/Keynest/pull/198). The broader design is tracked in [#199](https://github.com/JordyNicholas/Keynest/issues/199); review [the specification and interactive proposal](../../design/home-overview/DESIGN.md) before production routing or account-history tracking changes. After the bounded browsing/dialog refinement in [#203](https://github.com/JordyNicholas/Keynest/issues/203) and accordion motion in [#206](https://github.com/JordyNicholas/Keynest/pull/206), the maintainer approved the reviewed visual design and interactions for implementation. The public-home slice [#207](https://github.com/JordyNicholas/Keynest/issues/207) is merged through [#208](https://github.com/JordyNicholas/Keynest/pull/208). Shared account entry [#209](https://github.com/JordyNicholas/Keynest/issues/209) is merged through [#210](https://github.com/JordyNicholas/Keynest/pull/210). Bounded metadata queries [#211](https://github.com/JordyNicholas/Keynest/issues/211) are merged through [#212](https://github.com/JordyNicholas/Keynest/pull/212). Bounded Vault browsing [#213](https://github.com/JordyNicholas/Keynest/issues/213) is merged through [#214](https://github.com/JordyNicholas/Keynest/pull/214). The owner-scoped overview/account-navigation slice [#215](https://github.com/JordyNicholas/Keynest/issues/215) completes the previewed primary screens with working filter/item destinations; validate the actual result using [the Windows/Edge checklist](../../manager/account.md). Implementation does not close the human walkthrough or release gates. On November 21 the maintainer explicitly authorized merging all previewed changes through separate passing PRs; new updates and unresolved privacy/tracking choices still require chat discussion. Design approval is not evidence that these features have shipped. History scope/defaults/retention and generation tracking remain separate decisions; do not introduce telemetry by inference. Preserve separate production PRs, CI, the scoped approval recorded in AGENTS.md and existing security/release verification gates; this direction does not invent a new delivery date.

**Maintainer decision — November 16:** prioritize completing development **before** comprehensive testing, deployment/recovery rehearsals and release-candidate definition. This does **not** waive tests alongside code, CI, security reviews, PR approvals or fixing known security defects. The dedicated agent should continue non-conflicting work without waiting for new orders.

1. **Finish core development now (through November 21):** inspect latest `main`, PRs (including [#131](https://github.com/JordyNicholas/Keynest/pull/131) if still open), branches and issues. Finish and polish routed Material account/tools/vault CRUD, generation/validation, reveal/conceal, rotation, expiry, backend wiring, accessibility fundamentals and error states. **Acceptance:** implemented and merged scoped journeys, relevant unit/integration/security tests, green affected-PR CI, no known unresolved critical defect.
2. **Finish bounded AI only if non-blocking:** keep ephemeral BYOK explanation read-only, opt-in, provider-neutral, secret-free and disabled by default. **Acceptance:** fake-adapter and credential/egress negative tests; no persisted AI key, model-driven authorization or unrestricted MCP. Defer live-provider smoke until approved verification.
3. **Full verification after implementation (November 22–26):** run browser walkthrough, responsive/accessibility checks, real PostgreSQL/Compose startup, retained-key backup/restore and post-restore reveal, security/privacy review and AI-disabled regression. **Acceptance:** dated SHA/environment-linked actual results, resolved P0 failures and documented limitations.
4. **Release candidate only after verification (November 27–28):** freeze SHA, rerun all release CI gates, finalize documentation, distribution/version, demo and portfolio materials. **Acceptance:** exact-SHA passing CI, manual evidence, license/security disclosure and maintainer go/no-go. Delay public release if P0 gates fail.
5. **Keep post-release scope deferred:** MFA, recovery keys, enterprise RBAC/SSO, cloud KMS, broad MCP/agents, client-side zero knowledge and advanced reminders.
```text
Current main / issues / accepted ADRs
  └── Implement core Angular + API journeys (tests per PR)
         └── Optional narrow BYOK explanation (non-blocking)
                └── Nov 21 implementation-complete MVP
                       └── Nov 22–26 full-system verification + fixes
                              └── Nov 27–28 release candidate / go-no-go
                                     └── Adoption → keys/teams → AI/MCP → enterprise
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

**Decisions reserved for maintainer:** license; private security-reporting address; release version/distribution; optional live BYOK model/cost/privacy approval; final public-release go/no-go; later ADRs for per-user key custody, team sharing and agent identity/approvals. **Remaining §4A decisions:** primary audience for the next milestones (integrators or self-hosters); whether any hosted instance is in scope; MFA/TOTP promotion; scope and budget for an independent security review before agent features are default-on; Maven Central publication and compatibility policy; provider, model and cost for any live BYOK; first OIDC provider and detailed account lifecycle/rollout. Password change is already implemented under H4/ADR-0018, and the AI/agent trust ADRs are already accepted; do not treat them as missing decisions. Email/OIDC direction is scoped by #300–#302 and does not approve an unreviewed runtime diff.

## 8. Document hierarchy and upkeep

- **This file:** canonical end-to-end sequencing, release scope and agent execution priorities.
- [DELIVERY_PLAN.md](../../archive/delivery-plan.md): original overlapping November schedule and issue map; historical dates do not override this file.
- [MVP_RELEASE_EVIDENCE.md](../../archive/mvp-release-evidence.md): live Nov 21 candidate checklist and evidence links; update it as work progresses.
- [ROADMAP.md](../../archive/phased-roadmap.md): older technical breakdown, with historical Angular/version assumptions; reconcile against current code before using.
- [PROJECT_CHARTER.md](charter.md) / [PRODUCT.md](product.md): mission and product requirements.
- [AI_ARCHITECTURE.md](../../design/ai-and-agents.md) / [AI_BACKEND.md](../../api/policy-explanations.md) / [ADR-0010](../../architecture/decisions/ADR-0010-ephemeral-byok-policy-explanation.md): proposed AI evolution and implemented narrow contract.
- [THREAT_MODEL.md](../../security/threat-model.md) and accepted ADRs: technical security authority.
- [AGENTS.md](../../../AGENTS.md) / [PROJECT_PR_WORKFLOW.md](../pull-requests.md): branch, review, approval and coordination rules.
**Maintenance:** update this master roadmap only for scope/phase changes; use issues, PRs and the evidence checklist for daily progress. Never rewrite the historical engineering baseline to imply later work existed at its pinned SHA.
