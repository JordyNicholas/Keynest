# Account-identifier sign-in throttling

Roadmap M1 H2 / #298. The client/route limiter alone permits independent attempts from different peers; this layer bounds attempts for one normalized email regardless of their client IP. It complements the [password-work mitigation](LOGIN_HARDENING.md) and existing shared Argon2 budget. Exact candidate results belong on the issue/PR; configuration or test source alone is not runtime evidence.

## Admission and failure accounting

Register and login now trim/lowercase email using `Locale.ROOT`, avoiding a server-locale-dependent identifier key. Admitted lookups still use the existing case-insensitive repository method. No existing address is rewritten or migrated; unusual legacy locale-dependent addresses retain their stored values and need separate compatibility evaluation. Blank credentials and login identifiers above the existing 320-character account bound receive the generic pre-lookup 401. This is not a new email-verification or registration-enumeration policy.

Before lookup, both known and unknown normalized identifiers use the same admission rule. One attempt may be in flight per identifier. A second concurrent attempt returns generic 429 with Retry-After 1, without queueing or reading account state. Every admitted nonblank attempt continues to verify one real/dummy hash through shared Argon2 capacity. Credential failures are counted after verification. Reaching the threshold starts a fixed cooldown; rejected requests do not extend it. Cooldown expiry permits a fresh failure window even if the earlier observation window had time remaining. An expired observation window resets a below-threshold count.

Successful verification and session creation clear that identifier's failure history. Service-level repository/verifier/session exceptions release admission without recording a password failure or erasing previous failures. The limiter is outside database durability: it is not transactional state, and a later transaction commit failure can follow a cleared successful attempt. It grants no authentication/session permission. Every permit is idempotently completed/released; active entries are never pruned, so an old completion cannot erase a newer reservation. Request disconnect is not a promise that server work stopped; active admission remains until the service exits. Operators still need database/request deadlines.

## Configuration and bounds

| Environment setting | Default | Accepted range |
| --- | --- | --- |
| `KEYNEST_LOGIN_THROTTLE_FAILURE_LIMIT` | 5 verified failures | 1–100 |
| `KEYNEST_LOGIN_THROTTLE_WINDOW_SECONDS` | 60 seconds | 1–3600 |
| `KEYNEST_LOGIN_THROTTLE_COOLDOWN_SECONDS` | 30 seconds | 1–3600 |
| `KEYNEST_LOGIN_THROTTLE_MAX_ENTRIES` | 10000 identifiers | 1–100000 |

Settings bind under `keynest.login-throttle`. Invalid settings fail startup. These controls remain enabled independently of `keynest.rate-limit.enabled`; disabling the client limiter does not disable account admission. There is no durable account-lock flag or unlock/bypass endpoint. Correct credentials are also refused during a cooldown.

Time comes from a monotonic process clock. Retry-After rounds positive remaining seconds up; responses carry no-store and fixed generic guidance, never the target email, existence, failure count or state classification. Full tables fail closed with the same 429 and Retry-After 1; a complete inactive-expiry scan runs at most once a second when capacity is needed. Direct access can reclaim its own expired entry immediately. No active entry is evicted or silently replaced to admit another identifier.

The bounded map holds HMAC-SHA-256 identifier keys, counters/timestamps and an in-flight flag. A fresh random process key is distinct from vault/session/provider keys, never persisted/exported/logged, and uses standard JCA HMAC. UTF-8/digest scratch arrays are cleared best-effort. No raw identifier, candidate password, user row, bearer or request body is stored in limiter entries; Java/runtime copies outside those owned buffers cannot be reliably erased. No new metric/log fields or dependency are introduced.

## Limits, recovery and rollout

An attacker can deliberately force a known identifier into cooldown. This is a temporary targeted denial-of-service tradeoff, not solved by a generic error message. A full table or stalled in-flight work can also deny other sign-ins. Both are documented availability limits; do not describe the table or hashing budget as distributed protection. Entries, counts and the process key reset on restart, and independent replicas have independent budgets. Retain client/route controls, correctly configured trusted proxies, ingress limits/deadlines and shared edge/account controls for a multi-replica deployment. Avoid excessively high thresholds or long lockouts without observed capacity/UX evidence. There is no measured production throughput or constant-time claim.

No DB/schema/key/hash/session format changes are required. Apply matching application/configuration, keep current sessions valid, and verify failure/cooldown/recovery through the intended ingress using disposable identities. Rollback removes this admission/configuration layer and restores the prior service wiring, reopening the multi-client guessing gap; it requires no data migration and must not alter the existing client limiter or Argon2 budget. No registration behavior, provider call, public deployment, release or cookie migration is enabled by this change. Registration still discloses duplicate emails pending the maintainer's separate decision; T01 and remaining H1 gates stay open.

## Verification contract

Deterministic monotonic-clock tests cover threshold/expiry boundaries, cooldown independent of the observation window, no extension on rejection, success clearing, infrastructure/cancel cleanup, full-table recovery, non-eviction of active entries, idempotent/stale permits, concurrent admission, clock wrap and invalid bounds/identifiers. Service tests cover verifier/repository/session errors and locale invariance. Real HTTP tests use separately resolved clients through an explicitly trusted loopback test proxy: changing IP cannot reset known/unknown identifier budgets; correct credentials are refused during cooldown; other identities and successful-reset behavior remain available; the original client limiter still applies. This test-only proxy trust does not change production proxy settings. Existing Java, PostgreSQL, both Compose checks and all four real browser lifecycle/file cases remain required.

## Primary guidance

- [OWASP authentication throttling and lockout tradeoffs](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#login-throttling)
- [Accepted browser transport](../../ADR-0007-session-token-storage-and-browser-transport.md)
- [Deployment controls](../../DEPLOYMENT.md)
