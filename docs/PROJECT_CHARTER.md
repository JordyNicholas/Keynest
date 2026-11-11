# Keynest — project charter

**Status:** Accepted shared engineering charter (PR #67, November 11, 2026)
**Delivery target:** November 28, 2026  
**Scope:** Reusable password/security libraries and Keynest Manager; optional AI-native developer and agent workflows

## Mission and audience

Build a credible, demonstrable, open-source security product that is useful beyond its portfolio value. Serve developers, integrators, self-hosters, and small engineering teams first; company-grade features may evolve later. Show disciplined software engineering and a differentiated approach to AI-era credential workflows.

## Product principles

1. **Secure by design.** Standard cryptographic primitives, explicit key custody, least privilege, tenant isolation, secure defaults, versioned contracts, migration/backup plans, and honest risk disclosures.
2. **AI-native but AI-optional.** AI should enable meaningful new workflows, not a generic chatbot. Deterministic validation and authorization remain the source of authority; core vault and tools work without any LLM.
3. **BYOK-first.** Users supply their own AI provider credentials. No mandatory first-party hosted LLM, proprietary provider lock-in, or dedicated model per tool. BYOK credentials are separate from vault encryption keys.
4. **Infrastructure-agnostic.** Local and self-hosted deployment first; portable container and PostgreSQL path; cloud/Kubernetes integrations are optional adapters, not baseline dependencies.
5. **Excellent developer and user experience.** Java 21, Spring Boot 4, Angular 21, Angular Material, clean responsive UX, purposeful motion, keyboard accessibility, reduced-motion support, reliable API contracts, documented setup and tests.
6. **Evidence over appearances.** Avoid rewriting sound components without justification. Never market unimplemented features as shipped, or imply external audit/certification without evidence.

## Architecture posture

Preserve reusable `keynest-seal`, `keynest-policy`, and `keynest-generate` libraries as independent APIs, with Manager as first-party consumer. Prefer a modular monolith with clear feature boundaries and explicit ports for key providers, AI providers, notification, and other external integrations. Introduce abstractions only where they reduce meaningful coupling. Capture consequential choices in ADRs.

## Differentiation hypothesis

Explore a **policy-driven Agent Gateway**: an authenticated agent requests narrowly scoped capabilities; Keynest evaluates deterministic authorization and executes approved operations without returning vault credentials to the model. A safer first AI slice may be policy explanation/proposal with BYOK, schema validation, and human approval. These are proposed features, not claims of implementation or market uniqueness. Competitive analysis and threat modeling must inform final scope.

## Release boundary

Aim for a secure, working Manager and reusable libraries, polished critical flows, reproducible local deployment, meaningful demonstrable AI differentiation, and truthful documentation by November 28. Feature breadth may be reduced when evidence or time requires it. Security-sensitive unfinished features must remain disabled, documented, or block release. The release need not claim enterprise readiness, zero knowledge, or independent security audit.

## Coordination and authority

GitHub issues, ADRs, and reviewed PRs are shared records across AI providers. The maintainer is the final decision-maker for architecture trade-offs and all security-sensitive merges. Use focused branches and PRs, verify tests, and avoid concurrent edits to the same files. See [agent instructions](../AGENTS.md), [AI architecture](AI_ARCHITECTURE.md), [product](PRODUCT.md), [coding standards](CODING_STANDARDS.md), [verified baseline](ENGINEERING_BASELINE.md), and [delivery plan](DELIVERY_PLAN.md).

## Decisions requiring explicit review

- Key custody, envelope context binding, legacy-data migration, recovery semantics.
- Session/token storage and browser transport.
- BYOK provider credential persistence, egress and privacy model.
- Agent identity/scopes, tool capabilities, approvals and audit.
- Exact first-release AI demonstration and release claims.
