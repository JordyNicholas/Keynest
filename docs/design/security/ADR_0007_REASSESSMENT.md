# ADR-0007 reassessment — proposed browser transport amendment

Status: proposed, November 26, 2026; part of H1 / #278 and draft #279. This is a review proposal, not an accepted amendment or a cookie implementation. ADR-0007's accepted bearer transport remains authoritative.

## Source and recommendation

Reviewed main `9aaa1aaa964c68287c77045c60fb7a735fc15c56`. `AuthService` in Manager Web restores a bearer from `sessionStorage` and builds an Authorization header; API authentication returns a token in JSON. `KeynestConfig` permits exact configured origins without credentialed CORS. #281 adds API document headers; it does not protect the separately hosted Web document from XSS or remove JavaScript token access.

Propose a separately reviewed, same-origin HTTPS browser-cookie mode as the first migration target. Keep existing bearer deployments supported until that mode, CSRF protection and recovery have actual browser evidence. Do not make cross-site cookies the universal default or silently convert a bearer session. The supported split-origin setup is a compatibility requirement, not a reason to leave the XSS risk untracked. T02 stays open while that risk remains.

## Topology decision

| Topology | Proposed treatment and verification |
| --- | --- |
| Web and API behind one HTTPS origin | First cookie target. Route API through reviewed ingress; verify path preservation, no shared cache, forwarded-header trust, error responses and cookie pass-through. No credentialed CORS needed for this topology. |
| Distinct HTTPS origins under one site | Separate browser experiment: exact credentialed CORS, client credentials mode and CSRF remain necessary. Same-site does not mean same-origin; test untrusted sibling origins. |
| Vercel and unrelated API site | Preserve bearer compatibility initially. A cross-site cookie option requires explicit browser-policy evidence; `SameSite=None; Secure` does not override third-party cookie restrictions. No wildcard-origin workaround. |
| Local HTTP development | Preserve the existing development bearer path. A future local cookie fixture must be isolated; never infer an insecure production cookie fallback from localhost behavior. |

## Proposed cookie and request contract

- Browser session cookie candidate: `__Host-keynest-session`, `Secure`, `HttpOnly`, `Path=/`, no Domain, explicit `SameSite=Lax` for the first same-origin experiment. Decide final SameSite behavior from navigation/login tests; it is an additional defense, not a substitute for CSRF protection. Set no persistent cookie lifetime beyond server expiry; browser session restoration and browser-wide sharing must be documented instead of promising tab isolation.
- Browser login/register must issue a fresh opaque session and never return its usable session credential to JavaScript. Retain digest-only server storage. Separate browser and integrator transport contracts explicitly; reject ambiguous cookie-plus-bearer requests before domain handlers. A cookie must not grant a session broader permissions.
- Use an established synchronizer-token implementation, evaluated with Spring's maintained security facilities, rather than a new custom signing scheme. Bind CSRF tokens to their preauthentication/authenticated session, rotate on sign-in/session change, require them for unsafe browser requests, and fail before mutation when absent, stale or mismatched. Login/register/logout need coverage too. Bound preauthentication state by TTL, capacity and throttling before adding such an endpoint.
- Enforce exact allowed Origin on unsafe browser requests and define an explicit denial rule for missing/null origins in cookie mode. Check Fetch Metadata as an additional signal; it does not replace tokens or origin checks. GET/HEAD must not perform mutations. Do not put credentials or CSRF tokens in URLs or logs.
- A future cross-origin cookie mode requires explicit client credentials, exact server origin grants, credential allowance, appropriate Vary handling and rejected-origin tests. Current bearer CORS must not be loosened as a preparatory change.

## Migration and rollback requirements

Do not deploy a cookie transport merely by adding Set-Cookie. First specify transport selection and session provenance, any required migration, CSRF lifecycle, session rotation, and server-side revocation semantics. Fresh sign-in is required at the switch; revoke incompatible sessions and clear old browser storage. Verify logout revokes server state and expires the exact cookie tuple, including error and expiry paths. Existing backup/key recovery must remain unchanged.

Cookie sessions are shared across tabs on the same host; test sign-out and account switching across tabs, session restore, refresh and expired-session recovery. HttpOnly limits credential readout, but an XSS-capable page may still act with the user's session. Continue Web CSP, dependency and output-encoding controls.

Rollback must revoke incompatible cookie sessions, expire cookies, clear incompatible browser state and require fresh sign-in to the retained bearer mode. Do not reinterpret a cookie credential as a bearer or leave both transports live accidentally. Define the operator switch and rollback tests before runtime code is merged.

## Acceptance evidence still required

No cookie/CSRF/browser migration tests have run for this proposal. Required evidence includes each supported topology; CSRF missing/mismatch/login/logout failures; hostile/sibling/null origins; credential ambiguity and session fixation; expiry/revocation; cross-tab behavior; restart/restore; local development isolation; and rollback. Record source, browser/version, domain and ingress fixture, and sanitized outcomes. Maintainer review of this proposed amendment is distinct from implementation approval and does not close H1 alone.

## Primary references

- [Accepted ADR-0007](../../ADR-0007-session-token-storage-and-browser-transport.md)
- [MDN Set-Cookie attributes and prefixes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)
- [MDN credentialed CORS and third-party cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS)
- [OWASP CSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
