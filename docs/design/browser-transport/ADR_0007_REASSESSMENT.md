# ADR-0007 reassessment — proposed browser transport amendment

Status: proposed, refreshed November 27, 2026; focused design #331 under H1 #278 / #279. This is a review proposal, not an accepted amendment or a cookie implementation. ADR-0007's accepted bearer transport remains authoritative.

## Source and recommendation

Reviewed main `f13fda2d5b95748559cb48ad748dc500d679f421`. `AuthService` in Manager Web restores a bearer from `sessionStorage` and builds an Authorization header; API authentication returns a token in JSON. `KeynestConfig` permits exact configured origins without credentialed CORS. #281 added API headers, and #293/#295 subsequently delivered production Web CSP/header enforcement and four real same/split-origin normal/reduced-motion browser lifecycle cases. Those controls do not remove JavaScript token access. See the [current H1 evidence and remaining gates](H1_HEADERS.md).

Propose a separately reviewed, same-origin HTTPS browser-cookie mode as the first migration target. Keep existing bearer deployments supported until that mode, CSRF protection and recovery have actual browser evidence. Do not make cross-site cookies the universal default or silently convert a bearer session. The supported split-origin setup is a compatibility requirement, not a reason to leave the XSS risk untracked. T02 stays open while that risk remains.

## Topology decision

| Topology | Proposed treatment and verification |
| --- | --- |
| Web and API behind one HTTPS origin | First cookie target. Route API through reviewed ingress; verify path preservation, no shared cache, forwarded-header trust, error responses and cookie pass-through. No credentialed CORS needed for this topology. |
| Distinct HTTPS origins under one site | Separate browser experiment: exact credentialed CORS, client credentials mode and CSRF remain necessary. Same-site does not mean same-origin; test untrusted sibling origins. |
| Vercel and unrelated API site | Preserve bearer compatibility initially. A cross-site cookie option requires explicit browser-policy evidence; `SameSite=None; Secure` does not override third-party cookie restrictions. No wildcard-origin workaround. |
| Local HTTP development | Preserve the existing development bearer path. A future local cookie fixture must be isolated; never infer an insecure production cookie fallback from localhost behavior. |

## Transport selection and session provenance

Recommend an operator-selected, opt-in browser-cookie mode for a single same-origin HTTPS deployment first. Existing installations stay on bearer mode until an explicit switch and fresh sign-in; no detection from an untrusted request field, Host or Origin silently chooses an authentication mode. Separate catalogued browser issuance/CSRF routes from retained integrator bearer routes. Define their exact wire contract in the implementation review; this proposal does not reserve or enable new endpoints.

Persist or otherwise enforce server-controlled transport provenance for every usable session. Existing rows remain bearer sessions through an explicit reviewed migration. Cookie-issued sessions must not authenticate as Authorization bearers, and bearer-issued sessions must not authenticate as cookies. Reject mixed, duplicate or ambiguous session credentials before handlers, without echoing them. Verify every credential-issuing response: signup/activation, login, password change and session replacement must never return a cookie-session credential to JavaScript. Authenticated identity, Vault ownership, fresh password proof for sensitive actions and existing bulk-operation checks remain authoritative.

## Proposed cookie and request contract

- Browser session cookie candidate: `__Host-keynest-session`, `Secure`, `HttpOnly`, `Path=/`, no Domain, explicit `SameSite=Lax` for the first same-origin experiment. Decide final SameSite behavior from navigation/login tests; it is an additional defense, not a substitute for CSRF protection. Set no persistent cookie lifetime beyond server expiry; browser session restoration and browser-wide sharing must be documented instead of promising tab isolation.
- Browser login/register must issue a fresh opaque session and never return its usable session credential to JavaScript. Retain digest-only server storage. Separate browser and integrator transport contracts explicitly; reject ambiguous cookie-plus-bearer requests before domain handlers. A cookie must not grant a session broader permissions.
- Use an established synchronizer-token implementation, evaluated with Spring's maintained security facilities, rather than a new custom signing scheme. The reference inspected is Spring Security 7.2.0 documentation, not a dependency selection; verify compatibility with the checked-out Boot BOM and current filter/handler inventory before adding dependencies. Bind CSRF tokens to their preauthentication/authenticated session, rotate on sign-in/session change, require them for unsafe browser requests, and fail before mutation when absent, stale or mismatched. Login/register/logout need coverage too. Bound preauthentication state by TTL, capacity and throttling before adding such an endpoint.
- Enforce exact allowed Origin on unsafe browser requests and define an explicit denial rule for missing/null origins in cookie mode. Check Fetch Metadata as an additional signal; it does not replace tokens or origin checks. GET/HEAD must not perform mutations. Do not put credentials or CSRF tokens in URLs or logs.
- A future cross-origin cookie mode requires explicit client credentials, exact server origin grants, credential allowance, appropriate Vary handling and rejected-origin tests. Current bearer CORS must not be loosened as a preparatory change.

## Preauthentication and browser lifecycle

A future CSRF bootstrap may allocate only bounded, short-lived preauthentication state; it must not create an account/authenticated session, authenticate a caller or mutate Vault data. Decide its capacity/TTL and refusal behavior before implementation and prove expiry/restart/flood recovery. A preauthentication cookie cannot be promoted unchanged into an authenticated credential. Use a fresh session and CSRF binding on successful sign-in or replacement; an old token must fail. Missing/null/hostile Origin on unsafe cookie-authenticated requests is denied. Keep CSRF values out of URLs, logs and retained diagnostics.

The browser obtains only the non-session CSRF value through a reviewed bootstrap/refresh flow and sends it only to the intended same-origin API routes. Do not assume Angular's default cookie-based XSRF helper implements a custom synchronizer design. Centralize client handling and verify that credentials/CSRF headers cannot leak to unrelated destinations. An expired/cleared token requires a safe refresh or fresh sign-in, never replaying a Vault mutation automatically.

## Migration and rollback requirements

Do not deploy a cookie transport merely by adding Set-Cookie. First specify transport selection and session provenance, any required migration, CSRF lifecycle, session rotation, and server-side revocation semantics. Fresh sign-in is required at the switch; revoke incompatible sessions and clear old browser storage. Verify logout revokes server state and expires the exact cookie tuple, including error and expiry paths. Existing backup/key recovery must remain unchanged.

Cookie sessions are shared across tabs on the same host; test sign-out and account switching across tabs, session restore, refresh and expired-session recovery. HttpOnly limits credential readout, but an XSS-capable page may still act with the user's session. Continue Web CSP, dependency and output-encoding controls.

Rollback must revoke incompatible cookie sessions, expire cookies, clear incompatible browser state and require fresh sign-in to the retained bearer mode. Do not reinterpret a cookie credential as a bearer or leave both transports live accidentally. Define the operator switch and rollback tests before runtime code is merged.

## Acceptance evidence still required

| Boundary | Required rejection/recovery evidence before cookie implementation is accepted |
| --- | --- |
| Transport provenance | Existing-row upgrade; cookie-as-bearer and bearer-as-cookie; duplicate/mixed credentials; every session-issuing response; explicit operator switch and rollback |
| CSRF bootstrap/admission | Missing, stale and mismatched tokens; fixation; capacity/TTL/restart/flood recovery; no account/session/Vault action from link/GET prefetch |
| Browser/Origin | Hostile, sibling, null and missing origins; forms as well as fetch; cross-tab logout/account switch, refresh, expiry and browser session restore |
| Security/lifecycle | Central default-deny inventory, owner denial, password change/revoke-all, bulk reauthentication, no-store download, restore uncertainty, legacy retained-key recovery |
| Ingress | Actual secure attributes through the supported proxy, no credential logging/caching, path/cookie pass-through, exact origins/trusted peers; current same-origin fixture is HTTP and does not prove secure-cookie or public TLS behavior |


No cookie/CSRF/browser migration tests have run for this proposal. Required evidence includes each supported topology; CSRF missing/mismatch/login/logout failures; hostile/sibling/null origins; credential ambiguity and session fixation; expiry/revocation; cross-tab behavior; restart/restore; local development isolation; and rollback. Record source, browser/version, domain and ingress fixture, and sanitized outcomes. Maintainer review of this proposed amendment is distinct from implementation approval and does not close H1 alone. #331 tracks this design decision; #278 retains implementation/ingress gates and #268 retains supplementary environment/recovery checks.

## Primary references

- [Accepted ADR-0007](../../architecture/decisions/ADR-0007-session-token-storage-and-browser-transport.md)
- [MDN Set-Cookie attributes and prefixes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)
- [MDN credentialed CORS and third-party cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS)
- [OWASP CSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

- [Spring Security CSRF facilities](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), inspected November 27, 2026; implementation/version compatibility remains unverified.
