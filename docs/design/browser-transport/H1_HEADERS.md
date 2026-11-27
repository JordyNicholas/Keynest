# M1 H1 — header evidence and remaining transport gates

**Status:** header implementation is merged; the browser-cookie amendment remains proposed. Refreshed November 27, 2026 for #278 / focused #331 and #279. This page records evidence, not deployment or residual-risk acceptance.

## Source and history

Implementation snapshot: `f13fda2d5b95748559cb48ad748dc500d679f421`, after approved #327/#329. The initial source inventory at `e007c1bfda7c7071c1c436649084605f9dd88417` found no configured Web CSP or shared API-header policy. That is historical: the [original draft](https://github.com/JordyNicholas/Keynest/blob/74335d1fa200ad9fc4458f69842dac816a4c4e9f/docs/design/security/H1_HEADERS.md) preserves the investigation, not current missing-control claims.

## Delivered controls and evidence

| Surface | Implemented behavior | Evidence / limit |
| --- | --- | --- |
| API | Shared nosniff, no-referrer and framing denial; existing CORS/no-store/error behavior retained | #281; real HTTP/rejection/preflight tests and both Compose paths; [deployment contract](../../operators/deployment.md#api-response-headers-and-https-ingress) |
| Static Web | Compiled API destination controls document CSP; both Vercel roots and portable policy output supply response framing controls | #293; [production policy](../../security/controls/web-csp.md); static JSON alone is not the full emitted document policy |
| Scripts/styles/fonts | Self scripts without inline/eval exceptions; build critical CSS inlining disabled; documented style-only inline exception plus exact Google style/font origins | Angular/Material compatibility is not a nonce-based style policy or an independent XSS proof; see the policy source/guide |
| Browser and files | Real disposable PostgreSQL/API with production Web artifact: same/split-origin × normal/reduced motion; framing denial and Vault/password/session/file lifecycle | #285/#292/#295 and later password/session gates; [fixture scope](../../security/controls/web-csp-verification.md) |
| Latest candidate execution | All seven checks and dependency matching passed on approved #329 source `804b6b9da86e30fd3312b17472be8c16a0283e33`, with current main documentation | [Application CI 46042740602](https://github.com/JordyNicholas/Keynest/actions/runs/46042740602), [dependency 46042739468](https://github.com/JordyNicholas/Keynest/actions/runs/46042739468); merged-main verification is separate |

The emitted Web policy is defined in [web-security.mjs](../../../apps/manager-web/scripts/web-security.mjs); baseline API headers are in [ApiResponseHeadersFilter](../../../apps/manager-api/src/main/java/dev/keynest/manager/api/ApiResponseHeadersFilter.java). Metadata/password/file flows are exercised by the sanitized browser fixtures, not a public deployment. Main CI 46044080723 on that implementation snapshot reached the duplicate reload sequence and failed at `RESTORE_DUPLICATE_RELOAD`, after the earlier duplicate review and CSP checks passed. #328/#332 track finer investigation; it is not evidence of a comparison, durability or memory defect, and passing runs do not establish the cause.

## Remaining gates

| Gate | Current state / next step |
| --- | --- |
| Browser transport | ADR-0007 still uses a JavaScript-readable tab bearer. Review the [focused cookie/CSRF amendment](ADR_0007_REASSESSMENT.md); no cookie migration is implemented. |
| Same-origin HTTPS | Existing local HTTP forwarding fixtures do not prove TLS, Secure cookies or ingress behavior. A cookie implementation needs an isolated HTTPS/proxy fixture and negative/migration tests. |
| Actual deployment | Public hostname, TLS, HSTS/domain/subdomain readiness, proxy trust, framing headers and cache/cookie forwarding require the operator's real ingress check. Hosting remains deferred; no live host was probed. |
| Supplementary journeys | #268 retains platform, upgrade, cross-instance and post-commit lost-response checks. Historical manual evidence remains pinned to its recorded revision. |
| Security acceptance | T02 remains open. CSP lowers exposure but does not remove XSS-driven session use or bearer readout. No remaining High/Medium risk is accepted by this document. |

H1 #278 remains open. #331 isolates the transport-design review so that its approval does not close header/ingress, runtime migration or manual gates. PR #279 is now a focused #331 design candidate; its draft status is retained pending review. Existing [ADR-0007](../../architecture/decisions/ADR-0007-session-token-storage-and-browser-transport.md), [central authentication](../../security/controls/api-authentication.md) and [threat model](../../security/threat-model.md) remain authoritative.

The refreshed draft integrates main `a09c4b8bf4aade0e5fded3b52a61f1033b48e6d4`, including the approved design-acceptance receipt #330. That documentation-only merge does not change this implementation snapshot or resolve the browser investigation.
