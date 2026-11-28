# Verification state and delivery claims

Implementation foundation #339 under [accepted ADR-0021](decisions/ADR-0021-verified-email-activation.md) and remaining mail work #338. This adds internal database state and maintenance, not an email service, activation endpoint, or authentication method. Direct signup remains HTTP 410; existing owners retain access under the V8 eligibility migration.

## Authority and transaction boundaries

V9 starts with empty recipient, client, admission, challenge and delivery-job tables plus one coordination row. It changes no existing owner, session or Vault data. The job table holds a challenge reference and claim/status/times only. Recipient/address/purpose/owner/session metadata belongs to the associated challenge; no raw token, password, password hash, full message or Vault key enters either table through this API.

`VerificationRequest` is an **internal already-parsed input**, with bounds, ASCII/control-character and canonical/delivery correspondence checks. It is not a mailbox syntax parser. A reviewed maintained mailbox parser, trusted-ingress client identity resolver, authenticated server-derived owner/address/session, and fresh password proof are prerequisites for the later public adapter. No HTTP handler calls this state API yet. Existing-owner inputs must match the current stored address and an active session belonging to that owner; already verified/ineligible owners cannot obtain that purpose here.

Short Spring transactions coordinate all replicas through the same database row. The store uses writable READ_COMMITTED transactions, JDBC statement timeouts and a ten-second transaction timeout. Incoming account transactions with incompatible isolation or read-only state fail closed; the store does not silently join a stale snapshot. The coordination row is physically updated on every operation, and missing/corrupt state is unavailable rather than a reason for a process-local fallback. Driver acquisition/commit behavior is still an operator availability boundary, not a hard end-to-end deadline guarantee.

Existing-owner admission/consumption locks the owner before the coordination row. Worker eligibility reads do not take that owner lock. Consuming a token **requires an existing writable account transaction**; it returns the bound request and removes that generation, without creating/updating an owner or issuing a session. The later application use case must keep consumption and account creation/verification in the same transaction, after its own reviewed validation/proof. The tests exercise rollback with a real JPA account write and concurrent consumption through independent store instances. [Spring's JPA transaction manager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html) supports JDBC participation with the same DataSource; actual H2/PostgreSQL evidence is required rather than inferred from that facility.

## Admission and metadata bounds

The accepted defaults are fixed in this foundation; there is no operator flag to disable them or enable unverified signup.

| Resource | Enforcement |
| --- | --- |
| Recipient, combined purposes | Three accepted intents per rolling hour; at least 60 seconds between them. A recipient refusal does not replace a generation, add a recipient admission or move its cooldown. |
| Installation | 100 accepted intents per rolling hour, coordinated in the database. |
| Trusted client | Ten admitted attempts per minute, including otherwise generic recipient-suppressed attempts. Client/global/capacity denials add no event to their denied budget. |
| Pending state | At most 1000 recipient-plus-challenge slots and 1000 client keys. A worst-case two-slot reservation is checked before recipient lookup, so a full installation also refuses a resend rather than making capacity results recipient-dependent. |
| Worker claims | Two unexpired claims installation-wide. Thirty-second leases exceed the accepted future fifteen-second mail-attempt deadline. This caps authorized claims, not arbitrary paused-process/physical connection behavior. |
| Lifetimes | Five minutes for an unclaimed intent; ten minutes for a minted token, checked independently of cleanup. |

Client keys are SHA-256 lookup values derived from the trusted identity. This reduces raw address retention; small-domain client identities can be guessed, so these are **not anonymous data**. Mail metadata and digests remain sensitive, access-controlled transient database data, including in backups. Recipient refusal is an internal result that a later public request handler must map to the same generic 202 body; no recipient cooldown, challenge ID, digest or delivery result may be exposed. Global/client/capacity admission precedes recipient suppression and public account lookup. This is not a constant-time or complete enumeration defense, and does not accept T01.

## One generation, one delivery attempt

An accepted same-purpose resend atomically replaces its prior generation; the other purpose remains intact. Combined recipient quotas survive replacement, consumption, session revocation and ordinary challenge cleanup. A denied resend cannot invalidate a usable link. Public requests against a known account are admitted without synchronous account lookup, then suppressed when claimed; they mint no token and cannot claim that account.

| State | Allowed transition |
| --- | --- |
| Queued | Before intent expiry, one worker transaction claims it, creates a 32-byte CSPRNG base64url token, and commits only its SHA-256 digest/binding/expiry. |
| Claimed | Immediately-before-send checks must still find this generation/claim, live lease/token and current owner/session/address or absent new-account identity. No database transaction is retained over caller I/O. |
| Finished | Fixed `SENT`, `FAILED`, `AMBIGUOUS`, `SUPPRESSED` or `ABANDONED` metadata; it never becomes queued again. |
| Consumed/replaced/revoked | The generation is deleted, cascading its reference-only job; its token cannot authorize a later operation. |

A worker claim must own its transaction: it refuses an already active caller transaction, so raw token material cannot escape before its own commit. Database/commit uncertainty returns no material; an actually committed claim remains claimed even if the caller receives an error. Expired claims become abandoned, never replayed/reconstructed after restart. A link that may already have been delivered remains consumable until its token expires or its generation is revoked; delivery failure/uncertainty is not an authentication result. Explicit rate-limited resend is the recovery path for a lost mail attempt.

`VerificationDelivery` redacts its representation and gives the worker only a temporary char-array copy, cleared after the callback even on failure. Closing clears owned token memory and refuses further copies; it cannot retract a copy/in-flight operation already handed to a caller. JVM, driver and later MIME/HTTP copies cannot be promised erased. No store SQL receives raw token bytes, and ordinary data/transaction exceptions are sanitized without driver causes, including before framework rollback reporting. Keep SQL/bind-parameter tracing off; supported diagnostics are fixed outcomes, not user metadata or full exception payloads.

Foreign keys bind existing-owner challenges to both owner and initiating session. Session deletion/replacement cascades those generations/jobs; expiry and current-address checks also refuse confirmation before physical cleanup. This does not implement the public fresh-password request flow or equivalent provider-only proof; those remain separate reviewed application boundaries.

## Maintenance, recovery and remaining enablement

Maintenance runs one bounded pass each minute and on admission/claim. Each deletion/abandonment statement touches at most 100 selected rows, oldest first; job deletion cascades only from those selected challenges. Unneeded quota keys are removed after their events/requests are gone. With bounded allocation and a healthy running service this keeps transient metadata below the accepted 24-hour retention ceiling. Offline/failed maintenance and database/mailbox backup retention remain operational responsibilities. Every authorization/expiry decision is checked at use time regardless of maintenance progress. Maintenance never claims a job, sends mail or creates an account.

A database rollback can revive pending/consumed/claimed state. This schema is not an external anti-rollback ledger. **Before any future activation/delivery enablement after restore**, the reviewed recovery runbook must invalidate all pending generations/jobs and revoke restored sessions, with a real retained-key restore rehearsal. The current signup pause and absence of a sender prevent public use of these tables during the staged implementation; do not wire public endpoints or delivery around that recovery requirement.

SMTP TLS/credentials/origin custody, actual connection deadlines, mailbox parsing, strict bounded public DTOs, route inventory/CORS/CSRF behavior, ordinary post-activation sign-in, same-session legacy confirmation, browser fragment custody and real disposable-mailbox delivery evidence remain #338 work. Action codes and optional OIDC remain #302 and #301. This foundation does not close H1, T01 or M1, certify deployment, or publish a release.
