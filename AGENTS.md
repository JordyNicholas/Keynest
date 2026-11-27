# Keynest — instructions for engineering agents

These instructions apply to the repository. Read them before planning, editing, reviewing, or merging. They apply to ChatGPT Work, Cursor, Claude, and human contributors alike; no agent's chat history is authoritative.

**Canonical execution roadmap:** [docs/contributing/project/roadmap.md](docs/contributing/project/roadmap.md) is the start-to-finish plan and current release-scope/priority authority. Read it before choosing the next task. Accepted ADRs and explicit maintainer decisions still govern technical/security contracts.

## Source of truth and coordination

1. Read the adopted [Project Charter](docs/contributing/project/charter.md), [Product](docs/contributing/project/product.md), [Coding Standards](docs/contributing/coding-standards.md), and applicable accepted ADRs. Read [AI Architecture](docs/design/ai-and-agents.md) for AI or agent work; it remains a proposed design boundary until its decisions land through ADRs and implementation PRs.
2. Use [Engineering Baseline](docs/archive/engineering-baseline.md) as the verified historical snapshot at its pinned commit. Use [Master Roadmap](docs/contributing/project/roadmap.md), current `main`, GitHub issues, reviewed PRs, and CI evidence for current status and sequencing. Do not rewrite the baseline to imply that later work existed at its pinned commit.
3. GitHub issues and reviewed PRs track work. Before changing files, inspect open PRs, active branches, and recent commits; avoid overlapping edits. Use `KN#<issue>-<slug>` branches and link the issue in the PR.
4. Distinguish `[verified]` implementation facts, `[proposed]` designs, and `[needs verification]` assumptions. Never claim tests, security audits, deployment, or AI capabilities that have not been verified.
5. Preserve established public contracts and existing user data unless an approved migration exists. Prefer incremental changes over rewrites without evidence.

## Product invariants

- Security-first, AI-native, BYOK-first, provider-neutral, infrastructure-agnostic.
- Java 21 / Spring Boot 4 backend, Angular 21 / Material frontend, reusable Java libraries, PostgreSQL production path, Docker deployment. Stack changes require an ADR.
- Core tools and vault must work with AI disabled and without cloud-specific services.
- A meaningful AI differentiation is required, but not at the cost of safe key custody, correctness, or a credible release.
- Release scope follows the canonical roadmap and recorded acceptance gates.

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
- Never write directly to `main`, force-update another agent's branch, bypass branch protection, or merge security-sensitive changes without the maintainer's **explicit approval of that reviewed change**. Feature changes also require maintainer approval in chat. Documentation-only PRs can merge after CI passes.
- If instructions conflict, raise a specific decision in an issue or ADR; do not silently choose an interpretation.

## Scoped maintainer authorization — November 21, 2026

The maintainer explicitly approved implementing and merging **all changes shown in the reviewed home/overview/Vault design previews**, with a separate PR for each focused slice and passing CI. This includes the public homepage, shared dialog-first account entry with safe deep-link return, the owner-scoped metadata overview, and bounded/filterable/searchable Vault browsing with expandable actions and reduced-motion support. See [the approved design and rollout](docs/design/home-overview/DESIGN.md) and #199. Do not request the same approval again for these scoped changes.

This authorization does not waive tests, security boundaries, separate PRs, the human release walkthrough or release gates. New scope still requires chat approval. Unresolved durable history, generation counters/provenance, opt-in defaults, retention and clearing contracts are not approved by inference. Changes to cryptographic custody, session transport or other architecture beyond the reviewed design retain their existing explicit-review requirements.
