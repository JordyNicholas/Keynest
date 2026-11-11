# Keynest — instructions for engineering agents

These instructions apply to the repository. Read them before planning, editing, reviewing, or merging. They apply to ChatGPT Work, Cursor, Claude, and human contributors alike; no agent's chat history is authoritative.

## Source of truth and coordination

1. Read [Project Charter](docs/PROJECT_CHARTER.md), [AI Architecture](docs/AI_ARCHITECTURE.md), [Product](docs/PRODUCT.md), [Coding Standards](docs/CODING_STANDARDS.md), and applicable ADRs.
2. Use [Engineering Baseline](docs/ENGINEERING_BASELINE.md) and [Delivery Plan](docs/DELIVERY_PLAN.md) once their baseline PR is merged; until then, review their proposed contents on `KN#64-engineering-baseline` and verify against the latest commit. Do not treat unmerged branches as adopted decisions.
3. GitHub issues and reviewed PRs track work. Before changing files, inspect open PRs, active branches, and recent commits; avoid overlapping edits. Use `KN#<issue>-<slug>` branches and link the issue in the PR.
4. Distinguish `[verified]` implementation facts, `[proposed]` designs, and `[needs verification]` assumptions. Never claim tests, security audits, deployment, or AI capabilities that have not been verified.
5. Preserve established public contracts and existing user data unless an approved migration exists. Prefer incremental changes over rewrites without evidence.

## Product invariants

- Security-first, AI-native, BYOK-first, provider-neutral, infrastructure-agnostic.
- Java 21 / Spring Boot 4 backend, Angular 21 / Material frontend, reusable Java libraries, PostgreSQL production path, Docker deployment. Stack changes require an ADR.
- Core tools and vault must work with AI disabled and without cloud-specific services.
- A meaningful AI differentiation is required, but not at the cost of safe key custody, correctness, or a credible release.
- Target release: **November 28, 2026**. Scope may shrink; security acceptance criteria may not.

## Security rules

- Do not invent cryptographic primitives or change the Seal envelope, AAD, key hierarchy, or golden vectors without an ADR, migration strategy, and negative/compatibility tests.
- Do not commit credentials, fixed production keys, decrypted secrets, sensitive request bodies, or raw tokens to logs, telemetry, fixtures, prompts, model outputs, PR comments, or docs.
- Authorization and policy enforcement are deterministic server-side decisions. Model output is untrusted and never grants permissions.
- AI provider keys are user-supplied credentials distinct from encryption keys; use explicit credential custody, deletion, redaction, and egress controls.
- Agent tools start read-only and least-privileged. Never expose raw vault secrets or provider keys to model context. Mutations require a separately approved, bound, expiring plan.
- Treat metadata as potentially sensitive; minimize model inputs, tool responses, traces, and audit payloads.
- Review tenant isolation, token handling, CSRF/CORS, trusted proxies, SSRF/egress, and migration/backups as applicable.

## Implementation and review

- Keep controllers thin, domain rules independent of transport and persistence, and libraries independent of Spring/AI/network concerns. Add ports only at meaningful external boundaries.
- Follow `.editorconfig`, Angular conventions, API contract consistency, accessibility, keyboard and reduced-motion support.
- Each PR: problem, approach, evidence, tests, compatibility/migration/deployment effects, risk, rollback, linked issue. No mass formatting or unrelated refactoring.
- Run relevant unit, integration, browser, PostgreSQL, and build checks when possible. If tools are unavailable, say so and do not imply success.
- Never write directly to `main`, force-update another agent's branch, bypass branch protection, or merge security-sensitive changes without the maintainer's **explicit approval of that reviewed change**. General authorization to work is not merge approval.
- If instructions conflict, raise a specific decision in an issue or ADR; do not silently choose an interpretation.
