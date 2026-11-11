# Keynest delivery plan — November 28, 2026

Status: working engineering plan, created November 11. Dates use America/Sao_Paulo. Owner: JordyNicholas. [Verified baseline](./ENGINEERING_BASELINE.md) · [Product](./PRODUCT.md) · [PR workflow](./PROJECT_PR_WORKFLOW.md).

## Product commitments

Deliver a credible, demonstrable release of the reusable password tools and Manager, preserving security-first engineering, AI-native workflows, BYOK-first provider access, portable infrastructure, and polished Angular Material UX. AI complements deterministic tools and must be optional at runtime. No dedicated model per tool is required.

The target is a tested release candidate with honest limitations. Calendar pressure cannot waive security acceptance criteria. If a gate slips, record the impact on the remaining dates and adjust feature breadth explicitly; do not represent incomplete work as shipped.

## Delivery sequence

| Dates (2026) | Outcome | Acceptance evidence |
| --- | --- | --- |
| Nov 11–12 | Baseline and architecture | Pinned assessment, CI and local build evidence; issue-linked documentation PR; review custody/session/AI decisions before implementation |
| Nov 12–16 | Security foundation | Production configuration rejects demo/missing keys; owner/item/policy binding and migration strategy; session digest/transport design; POST reveal/no-store and bounded client reveal; trusted proxy behavior; cross-user and negative tests. Maintainer approval before security merges |
| Nov 16–19 | Persistence and operations | Migrations + PostgreSQL tests; portable API/web configuration; health/readiness and sanitized audit events; Compose startup and restore rehearsal; notifications built from an idempotent outbox if retained in release scope |
| Nov 18–22 | Complete Manager UX | Register/login/logout, list/create/edit/delete, generate/validate, bounded reveal, expiry and guided rotate; Material forms/dialogs; keyboard/focus/reduced-motion checks; critical browser tests |
| Nov 20–24 | BYOK AI and agent slice | User-owned credentials through a provider port, one real provider adapter and a fake adapter for tests; optional AI policy explanation/proposal; schema validation and human apply; scoped read-only agent tools first; no secret-bearing tools exposed |
| Nov 24–26 | Adoption and integration | External library consumer builds independently of Manager; install/run instructions verified; selected license, disclosure policy, API examples, version/compatibility notes; full-stack browser rehearsal |
| Nov 27–28 | Release candidate and delivery | Security review findings resolved or explicitly block release; CI/package/Compose/browser evidence recorded; backup/restore confirmed; reviewed changelog and release artifacts; reproducible demonstration and known limitations |

Dates overlap for work that can proceed independently; dependencies still apply. AI networking/credential custody and agent mutation implementation wait on their security design. The table is a delivery target, not evidence that any future check has passed.

## Architecture decisions to record

Write focused ADRs using the existing numbered sequence as implementation work begins. Proposed decisions requiring review:

1. Key custody and binding: provider contract, KEK/DEK scope, development defaults, key rotation, legacy-data migration, recovery and metadata privacy.
2. Sessions: digest persistence, browser transport, expiry/revocation, CSRF/CORS implications and migration of existing sessions.
3. AI data and credentials: ephemeral BYOK first, provider adapters, allowed egress, timeouts/cost bounds, optional encrypted persistence and deletion, redacted telemetry.
4. Agents: identities/scopes, permitted fields, untrusted inputs, plan/apply approval binding, audit and denial behavior. The first slice can remain read-only; mutation is not implicitly authorized by a model's proposal.

Provider credentials, user passwords, decrypted vault values and full envelopes must never be model context. Even metadata can be sensitive: select minimal inputs with explicit user intent, not the whole vault. BYOK does not imply that third-party processing has no privacy consequences.

## Backlog use

| Work | Existing issues / treatment |
| --- | --- |
| Baseline | [#64](https://github.com/JordyNicholas/Keynest/issues/64) |
| Key configuration and threat model | [#30](https://github.com/JordyNicholas/Keynest/issues/30), [#43](https://github.com/JordyNicholas/Keynest/issues/43); extend with focused implementation tasks |
| Material, shared primitives and vault flows | [#25](https://github.com/JordyNicholas/Keynest/issues/25), [#26](https://github.com/JordyNicholas/Keynest/issues/26), [#31–35](https://github.com/JordyNicholas/Keynest/issues/31) |
| Notifications | [#36–39](https://github.com/JordyNicholas/Keynest/issues/36); secret-free outbox and retry/idempotency coverage |
| Runbook, consumer and release docs | [#40–43](https://github.com/JordyNicholas/Keynest/issues/40) |
| Sessions, reveal, proxy, migrations, BYOK and agent boundaries | Create focused linked tasks when starting those changes; these are gaps in the old plan |
| Future capabilities | Keep [#45–48](https://github.com/JordyNicholas/Keynest/issues/45) deferred: MFA, recovery keys, general HTTP sidecar, client-side zero knowledge |

Do not bulk-close old issues from an assessment. Verify their definitions of done, add evidence and reconcile drift in the implementation PR.

## PR and merge rules

- Use one task issue, a `KN#<issue>-<slug>` branch, focused commits and `Closes #<issue>` in the PR.
- Describe the problem/result, tests, data/config/API compatibility, deployment impact and remaining risks. Mark unfinished implementation as draft.
- Security-sensitive changes include crypto/envelopes, keys, auth/sessions, authorization, reveal/export, AI credential custody/egress, agent permissions/approvals, and infrastructure settings that alter trust boundaries.
- **Do not merge security-sensitive changes without JordyNicholas's explicit approval of the reviewed change.** A green build or earlier project authorization does not replace that approval. Do not enable auto-merge or bypass repository review rules for these changes.
- Run checks appropriate to the change. Runtime work must pass Maven tests and Angular build where affected, plus focused security/PostgreSQL/browser checks for the behavior changed. Documentation-only PRs need link/path and diff checks; baseline runtime results are separately recorded.
- No direct writes to `main`, forced updates to shared branches, production deployment, or destructive data migration as part of routine implementation.

## Release acceptance

- Fresh local setup works with documented prerequisites and user-supplied secrets; cloud providers are optional. AI-disabled vault and tools work.
- Security regressions have negative tests; tenant isolation, key binding, session revocation, reveal caching and proxy behavior are demonstrated.
- Migration and backup/restore rehearsal preserves existing data; reset/recovery behavior is accurately described.
- Real PostgreSQL, packaged API, web build and core browser flows pass; UI is usable with keyboard and reduced motion.
- BYOK fake-provider tests and a sanitized real-provider smoke check pass without publishing credentials; invalid output, timeout, disabled/missing credentials and secret-redaction paths are covered.
- Agent tools deny unauthorized actions and never return secrets. If mutation tools ship, expired/replayed/mismatched approvals are rejected by deterministic server logic.
- A consumer uses the public libraries without Manager; license, security contact, changelog, runbook, compatibility and residual-risk notes are reviewable.

Delivery is complete only when the release evidence supports these claims. This document does not claim an audit, compliance certification, zero knowledge, organization readiness, or universal provider support.
