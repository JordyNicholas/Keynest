# ADR 0007 — Session token storage and browser transport

## Status

Accepted in maintainer-approved PR #72 on November 11, 2026.

## Context

Manager issues 256-bit opaque bearer tokens. Before this decision, it stored the usable token directly in `auth_sessions.token` and Manager Web persisted the same value in `localStorage`. A database disclosure therefore exposed active sessions. Browser persistence also survived browser restarts and made the token available to any script executing in the origin.

Keynest currently supports a split-origin topology such as Vercel web plus a separately hosted API. Moving immediately to cookies would require a coordinated decision for `Secure`, `HttpOnly`, `SameSite`, credentialed CORS, CSRF protection, local HTTP development, and deployment domains. This change reduces present exposure while keeping that decision explicit.

## Decision

1. Continue using a 32-byte CSPRNG opaque bearer token in the API response and `Authorization` header for the MVP.
2. Persist only the lowercase SHA-256 hex digest. Authentication and logout hash the presented token before repository lookup. The digest is a lookup value, never returned or logged.
3. Keep the physical database column named `token` for this compatibility slice while the Java model calls it `tokenDigest`. Existing rows contain 43-character raw Base64url tokens; digest lookup cannot match them, so deployment intentionally invalidates every existing session without a destructive schema migration.
4. Manager Web stores the bearer in `sessionStorage`, limiting persistence to the browser tab. On startup and logout it deletes the legacy `localStorage` value. Users sign in again after deployment.
5. Do not describe `sessionStorage` as an XSS boundary. A script executing in the application origin can still read the bearer. CSP, dependency hygiene, output encoding, short session lifetime, and future browser-transport work remain necessary.

## Consequences

- A database dump no longer contains directly usable bearer tokens. Because tokens have 256 bits of entropy, offline preimage recovery from their digests is impractical with standard assumptions.
- Logout deletes by digest, and expired/revoked/invalid sessions fail authentication.
- All sessions issued before deployment are revoked. This is an intentional security migration and user-visible reauthentication event.
- Tab closure removes browser persistence, and sessions are not shared automatically between tabs.
- The API response still contains a bearer token and JavaScript still handles it. HttpOnly cookies remain a possible future design, but require an ADR covering CSRF/CORS and actual deployment topology before implementation.
- A later versioned migration should rename the physical `token` column to `token_digest`; the current mapping avoids relying on `ddl-auto=update` for a destructive rename.

## Verification

- Integration tests assert that the raw token differs from the stored 64-character digest and that valid, invalid, expired, and logged-out sessions behave correctly.
- Unit tests cover deterministic digesting and input separation.
- Web tests cover sessionStorage persistence, legacy localStorage removal, and logout cleanup.
