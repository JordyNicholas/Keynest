# ADR 0020 — Agent identity, scope and operation boundaries

## Status

Proposed for maintainer review, November 27, 2026; #325. Design only; no MCP endpoint, token issuer, agent tool, lease, mutation or feature flag is implemented or enabled. Acceptance does not close M1 or authorize M3 enablement.

## Context and observed source

At `24727e837d771e247230f41e78ab17128f4ee1ec`, Manager has [central HTTP authentication](../../security/controls/api-authentication.md), owner-scoped Vault operations and browser session bearers under [ADR-0007](../../architecture/decisions/ADR-0007-session-token-storage-and-browser-transport.md). These are not scoped agent credentials. Existing [HTTP security events](../../architecture/decisions/ADR-0009-sanitized-http-security-events.md) are not the durable agent audit required by the roadmap. Native restore's process-local review state is not a reusable multi-instance approval service.

An agent, its host, tool descriptions, retrieved content and model output are untrusted. Metadata can disclose sensitive information even if passwords are absent. A protocol schema, plan hash, model assertion or OAuth scope alone cannot authorize an operation.

## Proposed decision

### First surface and identity

Start with authenticated read-only tools returning a reviewed projection of built-in policy rules and minimal capability status. No free-form prompts, arbitrary Vault search, user labels/URLs/counts, password generation/validation of real candidates, reveal/export/restore, provider keys or credential-administration tools. Any later metadata projection needs privacy/ownership review and bounded pagination. No arbitrary network or shell tool.

For a future remote HTTP MCP adapter, prefer the versioned MCP/OAuth resource-server model over inventing a Keynest authentication protocol. Select a maintained authorization server and a tested client-registration profile before implementation. No OAuth server is selected by this ADR. Human OIDC sign-in (#301), Manager sessions and agent access tokens are separate domains; an OIDC ID token/email or borrowed browser bearer does not become an agent grant.

Validate issuer, intended resource/audience, expiry, integrity/introspection and scope before application access. Bind an internal grant to owner, client identity, resource set, allowed actions and expiry, using server-derived ownership. Intersect token privileges with the current grant and current resource policy on every call. No email-based auto-link, user-supplied owner authority, delegation or ambient wildcard access. Reject ambiguous/multiple credential sources; never forward inbound credentials upstream. Scope names and token/refresh lifetimes remain implementation-review decisions, with bounded least privilege and revocation evidence required.

Manager's default-deny handler inventory must extend to the new transport or receive an equivalent reviewed gate. A separate servlet/MCP transport is not protected merely because MVC routes are inventoried. Unauthenticated/denied calls return no resource data and cause no side effects. Per-owner/client/process quotas and shared enforcement are required before multi-instance exposure.

### Human grant, revocation and audit

The authenticated owner grants specific resources/actions to a specific client through a trusted human UI, with fresh proof for sensitive changes. Keep that ceremony outside agent/model context; it cannot be satisfied by a tool parameter saying the user approved. Grant creation, widening, revocation and client credentials need a separately reviewed account-lifecycle contract. Email confirmation (#302) is not an agent grant, an MFA claim or a substitute for this approval.

Revocation/expiry must be checked at admission and again before an irreversible operation. Bind outstanding approvals to grant/resource versions; a policy/grant/credential change invalidates them. Revocation prevents future admission; do not claim it recalls a completed or already-dispatched external action. Define and test active-operation cancellation and authorization-server/cache failure behavior before release; fail closed when current authorization cannot be established.

Require a durable, access-controlled, redacted action record for grants and operations before mutation/leases. Record bounded opaque internal references, action/outcome and approved versions/expiry, without secrets, prompts, request bodies, arbitrary output, URLs or raw tokens. References themselves are sensitive metadata: approve retention, access and deletion rules. Reserve required audit state transactionally; unavailable audit must prevent a new privileged operation. A local console logger is insufficient.

### Later leases and mutations

Prefer an internal server-side operation reference over a transferable secret-bearing lease. A lease is authority limited to one owner/client/resource, reviewed operation and destination, time window and use count. No Vault/provider secret enters a model/tool response. Accepting a raw credential or arbitrary URL/headers/body from an agent is out of scope. Destination, response and side-channel review is mandatory before any credential injection: output redaction cannot make a malicious destination safe.

Before a later write, the server constructs a canonical plan from current state, including owner/actor/client/grant, action, resource and credential versions, exact reviewed operation/destination, expiry and a unique execution identity. The human UI shows a meaningful secret-free description/diff resolved by the server. Human confirmation binds that exact immutable plan with fresh required proof. Altered parameters, another actor/client, a stale version, expiry or replay fail before effect. Plan handles are unguessable, owner-bound and insufficient by themselves; use standard authenticated server state, not custom cryptography.

| Operation state | Allowed transition and effect |
| --- | --- |
| Proposed | Human may approve the exact current plan or cancel; no effect |
| Approved | Atomic claim rechecks ownership, grant, versions, expiry and audit readiness |
| Claimed | Exactly one worker may dispatch; concurrent/replayed claims cannot dispatch |
| Completed / failed before dispatch | Record known outcome; no automatic second execution |
| Outcome unknown after dispatch | Record uncertainty; stop automatic replay and require reconciliation |
| Cancelled / expired / revoked | Refuse new claims; do not imply reversal of a dispatched action |

For local database writes, coordinate claim, recheck, effect and outcome in the same reviewed transaction. For external actions, a crash can occur between remote commit and local outcome. Require destination-specific idempotency/reconciliation or refuse that operation; do not promise generic exactly-once execution. A retry must resolve the same execution identity, never silently create a second approval. Durable state and restart/multi-instance tests precede leases; copying restore's in-memory store is rejected.

Canary credentials and post-use rotation remain later hypotheses, not safety guarantees. Canary events must contain no credential; rotation is a separately approved write with failure/recovery semantics. Neither is available through the first read-only catalog.

## Required adversarial evidence and gates

- Wrong issuer/audience/client/owner/scope, expired/revoked grant, stale cached authorization and ambiguous credentials return no metadata or effect. Test actual protected-resource discovery and client interoperability separately from unit validation.
- A malicious tool description, metadata value, provider response or model instruction cannot expand projection/scope, authorize itself, request a secret or invoke another operation. Exercise output/log/error sentinels and indirect encoding paths; redaction has documented limits.
- Altered plan/destination/action/version, cross-client handles, double apply, concurrent workers, restart, rollback, clock/expiry boundaries and unavailable audit are denied or reconciled without duplicate dispatch.
- Disconnect before/after actual external commit yields a known or explicitly uncertain result, never an automatic retry. Cancellation/revocation during dispatch does not falsely report rollback.
- AI-disabled and agent-disabled configurations make zero provider/agent calls; all existing core, PostgreSQL, browser, independent-consumer and quality gates stay required.

Read-only beta still requires a scoped implementation/security review. Per the [roadmap](../../contributing/project/roadmap.md#m3--agent-beta-flagged), M3 needs H3, M2 keyring and this accepted ADR; mutation also needs durable audit/shared enforcement and the reviewed operation design. Independent security review is required before any agent capability becomes default-on. No agent rollout is authorized here.

## Alternatives, remaining decisions and rollback

Reusing Manager bearers is rejected: it gives agent clients excessive browser/Vault authority. Opaque scoped PATs could be a separate non-MCP integration, but do not establish OAuth/MCP interoperability; defer rather than invent a second credential system now. Standard MCP transport/authentication can coexist with application-specific approval state; no new wire protocol is justified by this paper spike.

Before implementation choose the authorization server/client profile, resource URI/scopes/lifetimes, grant UI/fresh proof, durable audit retention/store, supported first tools and exact denial contract. Later choose an operation/destination with idempotency evidence; do not advertise general leases until then. These are review gates, not defaults silently selected by code.

No runtime/schema/data migration follows from this proposal. Documentation rollback leaves all existing flows intact. Future disablement must stop new grants/admission, revoke or expire outstanding authority and preserve reconciliation/audit; removing a UI/flag alone is not revocation.

## References

[Primary-source standards spike](STANDARDS_SPIKE.md) records the versioned MCP/OAuth basis and implementation gaps. [ADR-0019](ADR-0019-ai-egress-boundaries.md) governs the separate proposed AI egress boundary; provider credentials never confer agent permissions.
