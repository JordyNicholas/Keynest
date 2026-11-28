# M1 H1 — header evidence and remaining transport gates

**Status:** header implementation is merged; the focused cookie/CSRF design boundary was accepted in #279/#331, while the runtime ADR amendment/migration remains unimplemented. Refreshed November 28, 2026 for #278 and the operator-check slice #333. This page records evidence, not deployment or residual-risk acceptance.

## Source and history

Implementation snapshot: `b8dc13974b0c9e3f7b1aa745acede65f59cdd76f`, after the approved #279/#332/#289/#290 batch. The initial source inventory at `e007c1bfda7c7071c1c436649084605f9dd88417` found no configured Web CSP or shared API-header policy. That is historical: the [original draft](https://github.com/JordyNicholas/Keynest/blob/74335d1fa200ad9fc4458f69842dac816a4c4e9f/docs/design/security/H1_HEADERS.md) preserves the investigation, not current missing-control claims.

## Delivered controls and evidence

| Surface | Implemented behavior | Evidence / limit |
| --- | --- | --- |
| API | Shared nosniff, no-referrer and framing denial; existing CORS/no-store/error behavior retained | #281; real HTTP/rejection/preflight tests and both Compose paths; [deployment contract](../../operators/deployment.md#api-response-headers-and-https-ingress) |
| Static Web | Compiled API destination controls document CSP; both Vercel roots and portable policy output supply response framing controls | #293; [production policy](../../security/controls/web-csp.md); static JSON alone is not the full emitted document policy |
| Scripts/styles/fonts | Self scripts without inline/eval exceptions; build critical CSS inlining disabled; documented style-only inline exception plus exact Google style/font origins | Angular/Material compatibility is not a nonce-based style policy or an independent XSS proof; see the policy source/guide |
| Browser and files | Real disposable PostgreSQL/API with production Web artifact: same/split-origin × normal/reduced motion; framing denial and Vault/password/session/file lifecycle | #285/#292/#295 and later password/session gates; [fixture scope](../../security/controls/web-csp-verification.md) |
| Verified merged baseline | All seven checks and dependency matching passed on main `b8dc13974b0c9e3f7b1aa745acede65f59cdd76f`, including all four real browser origin/motion lifecycles | [Application CI 46096956680](https://github.com/JordyNicholas/Keynest/actions/runs/46096956680), [dependency 46096956684](https://github.com/JordyNicholas/Keynest/actions/runs/46096956684); no source change/retry or image publication |

The emitted Web policy is defined in [web-security.mjs](../../../apps/manager-web/scripts/web-security.mjs); baseline API headers are in [ApiResponseHeadersFilter](../../../apps/manager-api/src/main/java/dev/keynest/manager/api/ApiResponseHeadersFilter.java). Metadata/password/file flows are exercised by the sanitized browser fixtures, not a public deployment. Historical main CI 46044080723 on `f13fda2d5b95748559cb48ad748dc500d679f421` reached the duplicate reload sequence and failed at `RESTORE_DUPLICATE_RELOAD`, after the earlier duplicate review and CSP checks passed. #328 records this and later intermittent stages, finer diagnostics merged through #329/#332/#290, and the maintainer's subsequent successful manual walkthrough with unspecified source/environment. No comparison, durability or memory defect is established, and passing runs do not establish the cause.

## Remaining gates

| Gate | Current state / next step |
| --- | --- |
| Browser transport | ADR-0007 still uses a JavaScript-readable tab bearer. The [focused cookie/CSRF boundary](ADR_0007_REASSESSMENT.md) was accepted through #279/#331; no runtime amendment or cookie migration is implemented. |
| Same-origin HTTPS | Existing local HTTP forwarding fixtures do not prove TLS, Secure cookies or ingress behavior. A cookie implementation needs an isolated HTTPS/proxy fixture and negative/migration tests. |
| Actual deployment | Public hostname, TLS, HSTS/domain/subdomain readiness, proxy trust, framing headers and cache/cookie forwarding require the operator's real ingress check. The [opt-in response-contract checker](../../operators/https-ingress.md) under #333 supports selected checks; its loopback tests are not deployment evidence. Hosting remains deferred; no live host was probed. |
| Supplementary journeys | #268 retains platform, upgrade, cross-instance and post-commit lost-response checks. Historical manual evidence remains pinned to its recorded revision. |
| Security acceptance | T02 remains open. CSP lowers exposure but does not remove XSS-driven session use or bearer readout. No remaining High/Medium risk is accepted by this document. |

H1 #278 remains open. #331 isolated the transport-design review so its acceptance through merged #279 does not close ingress, runtime migration or manual gates. Existing [ADR-0007](../../architecture/decisions/ADR-0007-session-token-storage-and-browser-transport.md), [central authentication](../../security/controls/api-authentication.md) and [threat model](../../security/threat-model.md) remain authoritative.

The accepted AI/agent contracts recorded through #330 and the focused browser-transport design acceptance do not enable later runtime features or resolve the browser investigation. M1 exit and remaining threat decisions are not inferred from a design merge or a successful checker run.
