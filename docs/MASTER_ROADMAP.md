# Keynest — Canonical Product & Engineering Roadmap

**Status:** Active, maintainer-directed execution plan  
**Updated:** November 23, 2026 (America/Sao_Paulo)  
**Implementation-complete internal MVP checkpoint:** **Saturday, November 21, 2026**  
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

**Security cannot be waived for a date.** November 21 is an **internal implementation-complete MVP checkpoint**, not a release-candidate or full-system testing milestone; comprehensive verification follows on November 22–26, and release-candidate preparation follows on November 27–28; November 28 is the public-release target **only if P0 gates pass**. If they do not, keep the candidate internal, document the blocker and make a maintainer go/no-go decision.

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

### Phase 5 — November 16–21: complete MVP implementation (P0/P1)

**Goal:** finish the planned product before full release rehearsals. The November 21 checkpoint means **implementation complete**, not fully tested or publicly ready.
- Reconcile current `main`, open PRs and issue definitions of done before starting work; do not duplicate another agent's changes.
- Finish Angular/Material account, tools and vault UX: register/login/logout, list/create/edit/delete, bounded reveal/conceal, generate/validate, guided rotate, expiry views, responsive/accessibility fundamentals, error and empty states.
- Finish the associated Spring Boot/API integration, preserving crypto, auth, ownership, migration and key-custody boundaries. Fix critical security/correctness defects as soon as discovered.
- Keep **targeted unit/integration/negative security tests and PR CI** mandatory during implementation; repair failing PR checks before merge. Comprehensive human browser, retained-key restore and deployment rehearsals are scheduled **after** implementation.
- Complete only the existing narrow, optional BYOK policy explanation if core delivery is not threatened; AI stays disabled by default and cannot authorize actions or access vault secrets.
- Reconcile open UI issues [#25](https://github.com/JordyNicholas/Keynest/issues/25), [#26](https://github.com/JordyNicholas/Keynest/issues/26), [#31](https://github.com/JordyNicholas/Keynest/issues/31), [#32](https://github.com/JordyNicholas/Keynest/issues/32), [#33](https://github.com/JordyNicholas/Keynest/issues/33) with implementation. Do not assume open issues prove missing functionality.

**Exit:** release-scoped functionality implemented on `main`, affected PR checks passing, and an explicit list of any unfinished functionality or critical defects. No release-candidate declaration.

### Phase 6 — November 22–26: full-system verification and defect remediation (P0/P1)

**Goal:** test the implemented product end to end, then fix defects without adding scope.
- Perform a **human-operated** full-stack browser walkthrough with synthetic accounts: login, create, reload, reveal/conceal, edit/rotate, delete, logout, keyboard/focus, responsive and failure paths.
- Rehearse clean PostgreSQL/API Compose and separately hosted/built Angular setup, migrations, backup/restore with the **same separately retained vault key**, and post-restore login/reveal.
- Review golden vectors, AAD tamper/tenant denial, session revocation, key guards, no-store reveal, redaction and deployment settings. Verify core operation with AI disabled.
- Record actual SHA, environment, commands/results, failures and fixes in [MVP_RELEASE_EVIDENCE.md](MVP_RELEASE_EVIDENCE.md). A successful CI configuration, PR or Vercel status is not substitute evidence.
- Optional live-provider BYOK smoke requires maintainer-approved credential, cost and data-disclosure decisions. Disable/defer AI if it endangers P0.

**Exit:** verified complete journeys and recovery, critical defects resolved, and documented limitations. If critical defects persist, defer release candidacy.

### Phase 6B — November 27–28: release candidate, packaging and public go/no-go (P0/P1)

**Goal:** publish a credible, demonstrable open-source release only after verification.
- Freeze a candidate SHA and obtain **actual passing CI on that exact SHA** for Maven 21/25, Angular tests/build, independent consumer, packaged Compose and required security/regression gates.
- Finalize README/quickstart, changelog, threat and known-limitations disclosures, license/SECURITY.md, version/tag, honest distribution notes, synthetic demo and portfolio screenshots/walkthrough.
- Link browser, recovery and deployment evidence; confirm AI-disabled core and disable optional capabilities without safe evidence.
- Obtain maintainer go/no-go and required security/feature approvals. If any P0 gate fails, delay public release rather than weakening the gate.

**Exit:** public tagged release with evidence and maintainer sign-off, or explicit no-go. Stop the November deadline review after November 28.

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

## 5. Immediate dedicated-agent queue (implementation first)

**Maintainer verification and scope — November 23:** [the full report](MVP_RELEASE_EVIDENCE.md#november-23-maintainer-verification--native-restore-and-core-release-paths) records passing Chrome/Ubuntu runtime, browser/accessibility, AI-disabled, HTTP/CORS and separate-database retained-key recovery checks on `fda972f070af68e6d957f3c0d88e29181d23307b`, with exact environment, limitations and [green same-SHA CI](https://github.com/JordyNicholas/Keynest/actions/runs/45213003321). Native export/restore and reviewed duplicates are merged through #249 / accepted ADR-0016. Record only the explicitly reported human scenarios; Windows/Edge, unreported conflict choices and lost-response-after-commit behavior are not claimed. **Third-party migration is deferred and is not a release prerequisite.** The pinned Bitwarden harness merged through #247 but does not verify destination save/sync or enable downloads. Prioritize evidence/docs closeout, remaining targeted release checks and final candidate/go-no-go. Public hosting remains deferred; older queue entries below are historical and do not override this direction.


**Maintainer validation follow-up — November 22:** the requested account/reference polish (#220), consolidated tools and inline Account diagnostics (#221), and semantic Vault controls (#222) are merged through maintainer-approved PRs #223, #224 and #225. The maintainer is revalidating the interface; keep #199 and the release gates open for actual results. Preserve account policy, secret cleanup, authorization and reduced motion. Continue portability implementation through separate reviewed PRs while this walkthrough proceeds.

**Maintainer direction — November 22, Vault portability:** the agreed plan in [#154](https://github.com/JordyNicholas/Keynest/issues/154) uses destination-first migration exports, versioned native JSON, an authenticated encrypted backup container, and destination-specific JSON/CSV only after importer verification. The native format foundation is merged in [#217](https://github.com/JordyNicholas/Keynest/pull/217). The maintainer has explicitly approved the encrypted file codec in [#219](https://github.com/JordyNicholas/Keynest/pull/219) and [ADR-0012](ADR-0012-portable-backup-container.md); it does not implement an HTTP download or database restoration. The container is merged through #219. Authenticated native preview/download (#226) and the Vault workflow (#230) are merged through explicitly approved PRs #227/#231 ([API contract](VAULT_EXPORT_API.md), [accepted ADR-0013](ADR-0013-authenticated-vault-export.md)). The internal receiving-database restore foundation (#234) is merged through maintainer-approved [#235](https://github.com/JordyNicholas/Keynest/pull/235), including a verified PostgreSQL cross-database/key/restart test. Authenticated single-use preview/apply transport is merged through maintainer-approved [#241](https://github.com/JordyNicholas/Keynest/pull/241) / [accepted ADR-0015](ADR-0015-authenticated-native-restore.md), including packaged/hosted PostgreSQL/API round trips. Browser restoration is merged through maintainer-approved #245 (#244); see [the web contract and user guide](VAULT_RESTORE_WEB.md). Pinned destination importer verification under #246 merged through #247 / [the interoperability record](BITWARDEN_INTEROP.md). Further migration work is deferred by the November 23 direction above; keep unverified migration formats unavailable. Retain separate PRs, CI and chat approval for subsequent reviewed feature/security changes. This work does not imply a completed Windows/Edge walkthrough or release gate.

**November 20 validation follow-up:** the maintainer reported passing browser, accessibility, runtime and retained-key recovery checks on `ac03ce592db56ca52719dcaef00b2f6abe7154a1`, with policy consistency and UX defects. The fixes for [#192](https://github.com/JordyNicholas/Keynest/issues/192) and the selected shared account policy in [#193](https://github.com/JordyNicholas/Keynest/issues/193) are merged through maintainer-approved [#194](https://github.com/JordyNicholas/Keynest/pull/194) and [#195](https://github.com/JordyNicholas/Keynest/pull/195). Prioritize their targeted Windows/Edge and V6 recovery rechecks before AI expansion. See [the rehearsal record](MVP_RELEASE_EVIDENCE.md#november-20-maintainer-rehearsal). Implementation is merged; the affected human checks remain open before release gates close. See [the post-merge checkpoint](MVP_RELEASE_EVIDENCE.md#november-20-policy-and-ux-fix-checkpoint).


**Maintainer direction — November 20, tailored UX:** prioritize a complete, reviewed public homepage and signed-in account overview without rushing this feature. The temporary/dismissible shared-error refinement is merged in [#198](https://github.com/JordyNicholas/Keynest/pull/198). The broader design is tracked in [#199](https://github.com/JordyNicholas/Keynest/issues/199); review [the specification and interactive proposal](design/home-overview/DESIGN.md) before production routing or account-history tracking changes. After the bounded browsing/dialog refinement in [#203](https://github.com/JordyNicholas/Keynest/issues/203) and accordion motion in [#206](https://github.com/JordyNicholas/Keynest/pull/206), the maintainer approved the reviewed visual design and interactions for implementation. The public-home slice [#207](https://github.com/JordyNicholas/Keynest/issues/207) is merged through [#208](https://github.com/JordyNicholas/Keynest/pull/208). Shared account entry [#209](https://github.com/JordyNicholas/Keynest/issues/209) is merged through [#210](https://github.com/JordyNicholas/Keynest/pull/210). Bounded metadata queries [#211](https://github.com/JordyNicholas/Keynest/issues/211) are merged through [#212](https://github.com/JordyNicholas/Keynest/pull/212). Bounded Vault browsing [#213](https://github.com/JordyNicholas/Keynest/issues/213) is merged through [#214](https://github.com/JordyNicholas/Keynest/pull/214). The owner-scoped overview/account-navigation slice [#215](https://github.com/JordyNicholas/Keynest/issues/215) completes the previewed primary screens with working filter/item destinations; validate the actual result using [the Windows/Edge checklist](ACCOUNT_OVERVIEW.md). Implementation does not close the human walkthrough or release gates. On November 21 the maintainer explicitly authorized merging all previewed changes through separate passing PRs; new updates and unresolved privacy/tracking choices still require chat discussion. Design approval is not evidence that these features have shipped. History scope/defaults/retention and generation tracking remain separate decisions; do not introduce telemetry by inference. Preserve separate production PRs, CI, the scoped approval recorded in AGENTS.md and existing security/release verification gates; this direction does not invent a new delivery date.

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
