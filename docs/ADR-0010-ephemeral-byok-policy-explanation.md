# ADR 0010 — Ephemeral BYOK policy explanation

## Status

Proposed for maintainer review in #95. This document adds no runtime capability. Approval adopts a boundary for subsequent implementation; it does not approve that implementation's merge or enable provider traffic.

## Context

The charter requires optional, provider-neutral AI using user-owned credentials. Core libraries, vault, sessions, policy validation and readiness already work without AI. Provider networking would add credential custody and third-party data disclosure. The first implementation needs a narrow, testable path before policy mutations or agent permissions are introduced.

## Decision

### First workflow and authority

The first slice is **explanation only** for a selected built-in system password policy. An authenticated user explicitly invokes it after seeing the selected provider and the policy fields that will leave Keynest. There is no chat/free-text prompt, vault lookup, secret scoring, candidate-password input, policy proposal/apply or tool execution. The model has no authorization or policy-enforcement role.

The request contains only registered `providerId` and built-in `policyId`; reject unknown fields. The server resolves the policy and constructs a new scalar projection: length/category requirements, character/whitespace/ambiguity flags and optional max age. Exclude descriptions, denylists/regexes, user policies, account identifiers, vault metadata, passwords, envelopes and keys. Never serialize domain/request/configuration objects directly into a provider prompt.

The application returns bounded plain text under an `explanation` field plus the known provider/policy identifiers. Model text is untrusted: validate its shape/size and display as text, never HTML, executable Markdown, instructions or an applied policy. This workflow is an initial integration foundation; the eventual release demonstration must substantiate the project's broader AI value separately.

### Credentials and user controls

The browser submits the user-supplied provider credential for one invocation in `X-Keynest-AI-Key`; Manager session authentication continues to use `Authorization`. The key is a credential, never model input. Limit it to 4096 printable ASCII characters, reject line breaks and do not echo invalid values.

Keep it only in request-scoped memory and a transport-only wrapper whose string representation is redacted. Do not use a Java record with credential-bearing generated `toString`. Do not store it in a database, browser storage, URL, cookie, cache, retry queue, log, metric, trace, event or exception. The browser clears its input/reference in a finally path on success/failure/cancellation; the server clears mutable buffers best-effort. Immutable Java/JavaScript strings, client libraries and runtime copies prevent a formal erasure guarantee.

There is no persistence/readback/delete-key endpoint. Clearing the form discards Keynest's reference; it does not revoke the upstream provider credential. The user manages upstream revocation and billing with their provider. A future persisted-credential feature requires another custody/migration ADR.

### Provider port and egress

Use a typed `AiProvider` port, a deterministic fake adapter for tests and one real adapter initially. Choose/document that real adapter and its protocol/model in the implementation PR using the provider's official documentation. No particular vendor is required for core operation.

Users choose registered provider IDs, not endpoints, URLs, proxies, arbitrary model names or HTTP headers. The first real adapter owns a fixed HTTPS origin/path and operator-reviewed model configuration. Verify TLS normally; disable redirects, implicit proxy inheritance, tools, retrieval, browsing and outbound callbacks. Do not fetch URLs in model output. Custom/self-hosted destinations and proxies require a separate egress review; a provider-neutral port does not imply arbitrary URL support.

Credential transport is confined to the selected adapter's expected authorization mechanism and destination. A request must not carry credentials from another invocation/provider. Reject a response that contains the exact supplied credential before returning/logging anything; this is a narrow reflection check, not a guarantee against a malicious provider. The selected provider necessarily receives its key and selected policy fields, and its retention/privacy terms remain relevant.

### Bounded behavior and disabled mode

Initial ceilings for implementation and tests:

| Bound | Initial maximum |
| --- | --- |
| Inbound JSON body | 8 KiB |
| Provider credential | 4096 printable ASCII characters |
| Provider connection / total request | 3 seconds / 15 seconds |
| Provider response body | 32 KiB, enforced while reading |
| Generated output | 512 tokens where supported; explanation at most 4000 characters |
| Concurrent calls | 2 per API instance |
| Invocation rate | 5/minute per authenticated user and 20/minute per instance |
| Automatic retries | None |

Release limits may be lowered; raising them requires a reviewed rationale. Bounds reduce exposure but do not guarantee a monetary ceiling: provider pricing/token accounting vary, and in-memory per-instance counters multiply across replicas. Cancellation must stop local work promptly, though it may not prevent upstream billing.

AI is disabled by default. Disabled, unauthenticated, unsupported-provider/policy, invalid credential/body, capacity and rate-limit requests must produce **zero provider calls**. No provider network work occurs during startup, readiness, ordinary tools or vault operations. Provider failures never block core operation.

Return stable sanitized error codes for disabled, unsupported provider, missing/invalid credential, rate/capacity limit, provider-auth failure, timeout, unavailability and invalid output. Do not proxy provider bodies, headers, stack traces or request identifiers into public errors. Map session authentication separately from upstream provider authentication. Document exact HTTP mappings in the implementation contract.

### Observability and retention

Add only fixed action/outcome/status events consistent with accepted [ADR-0009](./ADR-0009-sanitized-http-security-events.md). Optional latency/token counts must be bounded numeric fields, with no prompt, completion, credential, policy text, user/provider request identifier or sensitive metadata. Do not enable HTTP wire/body/header capture in the application or collectors. No server-side prompt/history retention or provider conversations are created by Keynest; select any provider-side no-storage option explicitly and document remaining provider retention.

## Required implementation evidence

- Fake-adapter tests prove the exact allowed projection and absence of passwords, metadata, envelopes and credential sentinels from model inputs, logs, events and errors.
- Disabled/unauthenticated/malformed/unsupported/over-limit paths prove zero adapter calls. Distinct request/provider credentials cannot cross-contaminate.
- A local mock HTTP server proves destination/path/header handling, no redirects/proxy leakage, timeout/cancellation, streaming response-size and output bounds, upstream auth/failure handling and credential-reflection rejection. Mock destinations are test-only, never configurable by clients.
- Output tests reject invalid structure, extra fields, oversized content and instruction-like content being treated as actions. Browser rendering remains text-only and never changes deterministic policy/vault state.
- Browser tests prove explicit invocation/data disclosure, no credential persistence and cleanup on all completion paths.
- Core auth/vault/tools/readiness tests pass with AI disabled and with the fake provider unavailable. Existing Java 21/25, PostgreSQL/restore, container and browser gates remain mandatory.
- A real-provider smoke uses a maintainer-supplied test credential outside CI artifacts, an explicit cost/data-disclosure decision and only sanitized pass/fail evidence. No agent searches for, reuses or invents a credential to obtain that evidence.

## Scope and rollback

This ADR does not authorize provider-key persistence, tenant policy editing, MCP/agent access or mutation approvals. Those boundaries remain separate designs. The first slice can be disabled/reverted without migrating schemas, vault data, sessions or keys. No real-provider availability or AI capability is claimed until implementation and evidence land.

## References

- [Shared AI architecture](./AI_ARCHITECTURE.md) and [project charter](./PROJECT_CHARTER.md).
- [OWASP SSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html): reviewed for allowlist/redirect boundaries.
- [OWASP LLM prompt injection prevention](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html): reviewed for untrusted-input/output and least-privilege boundaries. These references inform the design; they are not evidence of implementation or an audit.
