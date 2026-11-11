# Keynest — AI and agent architecture

**Status:** Proposed design boundaries, not an implemented capability. See [Project Charter](PROJECT_CHARTER.md).

The first concrete BYOK boundary is proposed in [ADR-0010 — ephemeral policy explanation](ADR-0010-ephemeral-byok-policy-explanation.md). It narrows the initial workflow to authenticated, explicitly invoked explanations of built-in policy scalars, with per-request credentials and no policy/vault mutation. The broader proposal/agent workflows below remain future designs; approval of the ADR does not claim a provider integration exists.

## Goals

Enable useful AI-assisted security workflows with **user-owned provider credentials (BYOK)** and provider-neutral interfaces. No AI provider is mandatory for vault operation. The distinguishing opportunity is safe integration of agent workflows with deterministic policy and credential operations.

## Trust boundaries

- **Core:** crypto, vault ownership, identity, authorization, and policy evaluation are authoritative deterministic services; they must not depend on an LLM.
- **AI application boundary:** explicit use cases construct minimal, sanitized inputs and validate model outputs against schemas. Never execute generated instructions as privileged operations.
- **Provider adapter:** user-selected external LLM endpoint; enforce approved destination/redirect policy, timeouts, size/cost limits, cancellation, and redaction. BYOK does not eliminate third-party privacy implications.
- **Agent/MCP boundary:** authenticate clients and scope tool access; tool schemas do not confer permission. Reject unauthorized requests before invoking application use cases. Treat all agent/model text as untrusted.
- **Infrastructure:** cloud KMS, secret stores, observability backends, and hosted model gateways are optional adapters.

## BYOK lifecycle

Separate AI provider credentials from vault encryption keys. Define submission, use, update, revoke/delete, and optional persistence. Prefer explicit ephemeral credentials for the first slice if product UX permits; if persisted, encrypt with a documented custody hierarchy and prevent readback, logging, and telemetry disclosure. Never put provider keys in model prompts or MCP responses. Validate destinations to prevent server-side request forgery. Document which data leaves Keynest and obtain clear user intent.

## Provider-neutral design

Define an `AiProvider` port with typed request/response, supported capability metadata, error categories, and bounded execution. Build a fake adapter for deterministic tests; implement only one real provider initially. Provider-specific features are opt-in, not embedded in the domain model. AI-disabled and provider-unavailable behavior must degrade gracefully.

## First safe workflow

**Policy explanation/proposal:** an authorized user selects a policy and requests a plain-language explanation or proposed structured policy change. Send only explicitly selected non-secret fields. Validate the returned proposal against the existing JSON Schema and deterministic policy engine; show a diff and require a user confirmation before applying. Test malformed outputs, prompt injection, cross-user access, timeout, disabled provider, redaction, and approval mismatch. Do not let the model self-approve.

## Agent Gateway hypothesis

Longer-term differentiator: an agent requests a narrow capability for a particular task, resource, actor and duration. The server authenticates the agent, checks scopes and resource ownership, evaluates policy, and records a sanitized audit event. Prefer operation execution or short-lived capability over returning underlying credentials. Start with **read-only, non-secret** MCP tools. If write tools are introduced, use plan/apply: canonical plan, explicit human approval bound to actor/action/resource/version/expiry, one-time execution identifier, replay protection, and audit. No raw secret reveal/export or provider-key tools in model context.

MCP is a protocol/interface, **not** a security boundary. A plan hash or model instruction alone does not authorize anything.

## Non-goals for first release

- Autonomous secret reveal or credential export to AI clients.
- AI-managed master keys, unrestricted vault search, or unapproved policy changes.
- Requiring a particular model vendor or hosted AI service.
- Treating model-generated risk scores as enforcement decisions.
- Promising that no competitor offers similar features without market evidence.

## Architecture decisions and evidence

Before implementing provider networking, credential persistence, or agent mutations, record ADRs covering data disclosure, credential custody, egress, identity/scopes, approvals, retention, and audit. Use negative and integration tests. Mark shipped features only after code, CI, and runtime evidence exist.
