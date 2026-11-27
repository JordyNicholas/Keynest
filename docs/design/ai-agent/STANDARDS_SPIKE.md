# AI and agent standards alignment — M1 paper spike

**Status:** source/documentation research on November 27, 2026; #325. Proposed ADRs [0019](ADR-0019-ai-egress-boundaries.md) and [0020](ADR-0020-agent-trust-boundaries.md). No SDK installed, authorization server selected, network/provider credential used, interoperability run, outside integrator feedback or independent review is claimed. Research alone does not close M1.

## Observed Keynest boundary

Pinned source: `24727e837d771e247230f41e78ab17128f4ee1ec`. The accepted [ADR-0010](../../architecture/decisions/ADR-0010-ephemeral-byok-policy-explanation.md) plus `ai/OpenAiProvider.java` restrict explanation to a fixed destination and minimal scalar policy facts. [Central HTTP authentication](../../security/controls/api-authentication.md) inventories MVC access decisions, not a future MCP transport. Browser sessions, the fixed HTTP event logger and process-local restore review store are not agent scope, durable audit or a lease engine. See [AI backend evidence](../../api/policy-explanations.md); live provider verification is pending.

## Primary references and application

Versioned MCP **2026-07-28** pages were inspected directly; this is a research baseline, not a promise to implement all clients or a claim about every later protocol revision. Revalidate specification/errata and pin an SDK/version before implementation. OAuth 2.1 is referenced there as an IETF draft; do not relabel it an issued RFC.

| Source inspected | What it informs | Keynest proposal / unresolved gate |
| --- | --- | --- |
| [MCP HTTP authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization) | OAuth resource-server roles and registration/discovery model; HTTP and stdio differ | Prefer standard remote HTTP authentication; select authorization server and client profile before code. No stdio bridge proposed. |
| [MCP authorization security](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/security-considerations) | Intended-resource token checks, PKCE and separate upstream credentials | Reject another audience and token passthrough. Existing Manager/OIDC/provider credentials cannot be repurposed as agent grants. |
| [MCP discovery](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/authorization-server-discovery) | Protected-resource metadata and authorization-server binding | Validate metadata/issuer/client association and discovery egress; no arbitrary untrusted URL fetch or shared registration state. |
| [MCP security practices](https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices) | Confused deputy, SSRF, stolen state and excessive scope | Trusted per-client human grants, bound operation state and minimal tools; documentation/schema alone is insufficient. |
| [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html), especially §§2.1–2.3 | OAuth security BCP, code-flow protection and least-privilege resource/action restriction | Use a maintained implementation with tested redirect/PKCE, audience and privilege checks; it does not replace per-owner authorization. |
| [RFC 8707](https://www.rfc-editor.org/rfc/rfc8707.html) | Explicit target-resource indication in OAuth requests | Define a canonical resource URI and verify intended recipient; resource indication alone is not an ownership grant. |
| [RFC 9728](https://www.rfc-editor.org/rfc/rfc9728.html) | Standard protected-resource metadata and validation | Prefer existing discovery contracts over a proprietary Keynest discovery protocol; actual client conformance remains untested. |
| [OWASP SSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html) | Known-destination allowlists, redirect and network-layer defenses | Keep fixed adapters initially; private/custom endpoints, DNS/connection binding and proxies require separate review. A hostname check is not network isolation. |

The proposed application approval/lease state is a Keynest authorization rule layered behind a standard transport. None of these references supplies a universal safe credential-use or exactly-once protocol. No standards-backed claim of uniqueness, secret-free competitors or product superiority follows.

## Alternatives and next experiment

| Option | Assessment |
| --- | --- |
| Remote MCP plus established OAuth server | Preferred research direction; enables conventional resource/audience/scope separation. Selection, deployment, discovery egress, client support and adversarial tests remain work. |
| Agent given a Manager bearer | Reject: broad browser/Vault authority and wrong credential domain. |
| Keynest-specific PAT protocol | Defer: could support a separate scoped HTTP integration, but adds issuance/revocation burden and is not proof of MCP interoperability. |
| Local stdio bridge carrying a browser/provider secret | Defer: different process/host custody boundary; remote HTTP assumptions do not secure it. |
| Arbitrary provider or operation destination | Reject for first slice: turns credential custody into general SSRF/exfiltration risk. |

After design review and prerequisite approval, run an isolated synthetic-data experiment: choose a maintained OAuth implementation and pinned MCP SDK, exercise one non-secret built-in-policy tool with two owners/clients and no Vault secret access, then prove denial for wrong issuer/audience/scope/resource, revoked/expired grants, discovery redirects/SSRF and injected tool content. Record exact versions/SHA, HTTP behavior and sanitized results. Do not contact a paid AI provider to test protocol authentication. Seek integrator feedback before promoting a hypothesis to a product contract.

Lease experiments come later, after keyring, durable audit and a reviewed destination/operation. Include disconnect-after-commit, replay/concurrency, restart and reconciliation before claiming safe mutations. H1 transport/ingress, supplementary platform verification and remaining threat-model decisions stay open; no runtime/public deployment or release is implied.
