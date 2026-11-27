# Sign-in hardening: password-work parity

Roadmap M1 H2 / #296, following the source gap reproduced on `b54432542c1959664a5fc29a0a1b1c8e5ce9400d`. This focused change removes the unknown-account shortcut; it does not complete H2 or close T01. Exact-source runtime evidence is recorded on the issue/PR.

## Verification path

`AuthService` prepares one missing-account hash during service initialization, through the existing `PasswordHashingService.hash` and configured hasher. The fixed input is a non-account work target, not a default/user credential. It is never registered, persisted, returned or logged. Initialization fails if preparing the work target fails; no first-request/lazy initialization branch is introduced.

Each nonblank login reaching account lookup invokes `PasswordHashingService.verify` exactly once, using the stored account hash or the prepared missing-account hash. The verification result is computed before the account-existence decision; a missing account is refused even if that verification succeeds. Wrong-password and missing-account failures retain the same generic unauthorized status/reason. Missing/blank credentials retain their pre-lookup refusal, independent of whether any account exists. Legacy account passwords and stored hash profiles are unchanged.

Both verification paths use the existing nonqueued shared Argon2 capacity; saturation returns the same existing 503 for either. Unknown identifiers now consume password-work capacity intentionally. Existing per-client/per-route throttling remains; it is not per-account throttling or protection against distributed saturation. No unbounded dummy hash cache, sleep, new password profile, session format, database migration, runtime dependency or logging is added. One normal hash operation is added to each service initialization.

This is a work-path mitigation, not a constant-time guarantee. Database/cache behavior, scheduling, candidate length and historical hash work factors can still affect observable time. Tests assert actual calls/control flow rather than fragile wall-clock equivalence. Existing stored hashes are not rewritten or silently upgraded here.

## Evidence and remaining H2 work

Unit tests cover one prepared hash reused across unknown attempts, exactly one verification, unconditional refusal of a matching dummy, stored-hash verification, legacy success with digest-only session storage, unchanged invalid-input behavior, and shared-capacity rejection/recovery. Native Argon2 tests cover known-wrong, unknown, a known dummy input and actual successful sign-in. The existing packaged and hosted Compose smoke adds real HTTP known-wrong/unknown 401 checks without reading or printing refusal bodies. Existing Java, PostgreSQL, session and browser lifecycle gates remain.

Per-account failure throttling/backoff, its bounded concurrent state and distributed/lockout limitations, plus the registration-enumeration decision, remain separate H2 work. Registration still returns 409 for an existing address; changing only its wording/status while successful registration issues a session would not conceal that distinction. No registration/privacy decision is silently made by this timing slice. T01 remains open, as do the separate H1 ingress/transport-review gates.

Rollback reverts the prepared work target and login selection logic, reopening the known timing shortcut. No stored rows, passwords, sessions, keys, configuration or files need migration. Keep existing authentication/owner tests and infrastructure controls.

## Primary references

- [Spring Security: preventing password-verification timing attacks](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [OWASP authentication errors and timing discrepancy](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#authentication-and-error-messages)
- [Accepted browser transport](../../ADR-0007-session-token-storage-and-browser-transport.md)
