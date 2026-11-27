# ADR 0019 — AI egress and credential boundaries

## Status

Proposed for maintainer review, November 27, 2026; #325. Design only. This does not amend accepted ADR-0010, authorize live provider traffic, enable AI or close M1. Acceptance and implementation are separate reviewed steps.

## Context and observed source

At `24727e837d771e247230f41e78ab17128f4ee1ec`, [ADR-0010](../../architecture/decisions/ADR-0010-ephemeral-byok-policy-explanation.md) governs authenticated, default-off explanation of one built-in policy. [AiExplanationService](../../../apps/manager-api/src/main/java/dev/keynest/manager/ai/AiExplanationService.java), [PolicyFacts](../../../apps/manager-api/src/main/java/dev/keynest/manager/ai/PolicyFacts.java) and [OpenAiProvider](../../../apps/manager-api/src/main/java/dev/keynest/manager/ai/OpenAiProvider.java) implement a scalar projection, request-scoped credential and fixed HTTPS destination. The [backend contract](../../api/policy-explanations.md) records mock tests and remaining live-provider verification. The Account settings explanation UI exists; it is not an agent gateway or policy-editing capability.

M1 requires a separate egress-path design before M2 human-reviewed policy proposals. Provider independence must not become client-selected destinations, arbitrary prompt data or credential forwarding. This proposal extends the review framework; it makes no claim that every future control below already exists.

## Proposed decision

### Data and authority

Every AI use case gets an explicit scalar request projection, output schema and disclosure screen. Reuse the minimal built-in-policy explanation projection initially. Custom-policy proposals need a separately approved projection/diff contract: no automatic serialization of domain objects, descriptions, regexes, denylists, user free text, account identity, Vault metadata, candidate passwords, envelopes, files or credentials. Reject unknown input/output fields. New data categories require review before transmission, even when the user owns the data.

Model output remains untrusted text or a bounded proposal. It cannot call tools, select destinations, change configuration, authorize access or approve itself. A later proposal must pass the existing deterministic schema/policy validation and a separate human-confirmed version-bound apply step. Treat prompt injection as a reason to restrict capabilities, not something instructions or a detector can reliably eliminate. Vault/core operations remain independent of AI availability.

### Three distinct credential domains

| Credential | Allowed recipient and use |
| --- | --- |
| Manager session bearer | Keynest authentication only; never provider headers, prompts or model context |
| Per-invocation provider credential | The selected registered adapter's expected authorization header only; never a grant to Keynest data |
| Vault encryption keys and saved secrets | Existing trusted Vault/crypto process only; never AI payloads or AI credential storage |

Preserve ephemeral BYOK. No server-wide provider credential, browser/server persistence, readback, background key validation or cross-request/provider reuse. Clear owned mutable buffers/input state on completion/cancellation where possible; do not promise erasure of runtime String/HTTP/browser copies. A key-reflection check is defense in depth, not a guarantee against a provider that encodes or transforms it.

### Destination and network policy

Keep registered adapters with code-reviewed exact HTTPS origin/path and operator-reviewed model configuration. Clients select provider IDs, never URLs, hosts, ports, paths, headers, proxies or redirects. TLS hostname/certificate verification stays enabled. Disable redirects and inherited proxies; do not fetch URLs, tools, attachments or callbacks suggested by output. The current loopback mock seam remains test-only, not a production endpoint setting.

Fixed hostnames still depend on DNS, TLS, routing and operator infrastructure. They do not prove network isolation. Deployment must restrict outbound traffic independently, including metadata/private/link-local destinations, and account for IPv4/IPv6 and DNS changes. For the existing public provider, require a reviewed public destination path; do not rely on a one-time DNS lookup followed by an unchecked connection. Any future custom/private provider or outbound gateway requires its own approved destination-resolution/connection-binding, authentication, certificate, proxy and network-policy design with tests. Reusing the current adapter with an arbitrary URL is rejected.

### Bounds, failures and retention

Retain ADR-0010's current ceilings and no-retry behavior. A new adapter/use case must use the same or lower reviewed body/output/time/concurrency/rate bounds until separately approved; do not multiply invocation budgets through parallel fan-out. Admission and authorization happen before acquiring provider resources. Bound reads while receiving, reject unexpected encodings/shape, and cancel local work on timeout/interruption. Client cancellation does not guarantee upstream cancellation or prevent billing; a lost response must not trigger automatic replay.

Keep stable sanitized errors and fixed action/outcome events. Never retain prompts, completions, credentials, provider request IDs or sensitive data in logs, HTTP wire capture, traces, APM, crash artifacts or support bundles. This includes proxies/collectors outside the application. No automatic conversation history, retrieval or cache. Provider retention/training and pricing terms must be reviewed for the actual account/model when live verification is approved; request flags alone do not establish zero retention or a monetary cap.

## Required evidence before implementation/enablement

| Scenario | Required result |
| --- | --- |
| Disabled, anonymous, invalid body/provider/policy/key, over budget | Zero adapter/network calls; core workflows work |
| Secret-bearing unknown fields, mixed users/keys/providers, concurrent calls | Projection excludes sentinels; credentials never cross requests or destinations |
| Redirect, injected URL/header/proxy, private/metadata/DNS change | Refused by the reviewed connection/network boundary; no credential sent to another destination |
| Slow/oversized/encoded/refusal/tool/malformed response | Bounded cancellation/refusal, sanitized error and no retry |
| Exact/transformed secret reflection, malicious instructions | No privileged effect; reflection handling/redaction limits explicit; output never executable |
| Failure through browser, API, adapter and collector | No credential/prompt/body in retained diagnostics; inspect synthetic sentinels only |
| Unavailable provider or interrupted request | Core unaffected; uncertainty/billing limits shown; no automatic replay |

Use deterministic fake adapters and isolated mock servers first. A live smoke still needs an explicit provider/model, dedicated test credential, disclosure and cost decision outside CI artifacts. Passing a mock, static analyzer or documentation review cannot substitute for that approval/evidence.

## Alternatives, consequences and review gates

Arbitrary user endpoints and inherited proxy configuration are deferred: they enlarge SSRF/credential disclosure beyond the accepted adapter. Persistent BYOK keys and a shared gateway are deferred pending custody, retention and tenant-isolation ADRs. A network allowlist complements application checks; it does not replace payload minimization or provider trust.

No migration or runtime change follows from this proposal; rollback is a documentation revert and AI remains default-off. Before M2 proposals are enabled, require M1 exit, acceptance of this ADR, a reviewed proposal/apply contract, exact-source adversarial tests and explicit enablement approval. This proposal does not accept remaining threat-model risks.

## References and scope

- [Standards spike and applicability](STANDARDS_SPIKE.md), including primary OWASP SSRF guidance checked November 27.
- [AI architecture](../ai-and-agents.md), [threat model](../../security/threat-model.md), [roadmap M1/M2](../../contributing/project/roadmap.md#m1--hardening).
- [Agent trust proposal](ADR-0020-agent-trust-boundaries.md) is separate: an AI credential never becomes agent identity or authority.
