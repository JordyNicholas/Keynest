# Keynest threat model

**Status:** Original release review accepted in maintainer-approved PR #81; November 23 source-release boundary refresh below

**Snapshot:** `3e203bb` (November 11, 2026)

**Review trigger:** Revisit for every new trust boundary, authentication or key-custody change, and before a public release.

**Companions:** [Product](./PRODUCT.md), [AI architecture](./AI_ARCHITECTURE.md), [database migration runbook](./DATABASE_MIGRATIONS.md), and ADRs [0001](./ADR-0001-crypto-model.md), [0003](./ADR-0003-keynest-seal.md), [0006](./ADR-0006-manager-vault-key-and-aad.md), [0007](./ADR-0007-session-token-storage-and-browser-transport.md), and [0008](./ADR-0008-versioned-database-migrations.md).

This is a source-informed threat model for the implemented Keynest libraries and Manager. It is not a penetration-test report, independent audit, compliance attestation, or proof that a deployment is secure.

## Scope and evidence

In scope:

- `keynest-seal`, `keynest-policy`, and `keynest-generate` library behavior;
- Manager registration, login, logout, session authentication, and rate limiting;
- vault list, read, create, update, delete, and bounded secret reveal;
- tools endpoints, including development-only seal/open;
- PostgreSQL/H2 persistence, Flyway upgrades, backup, and restore expectations;
- browser-to-API transport, CORS, trusted proxies, logging, and runtime configuration.

After the original review, Keynest added [ephemeral BYOK policy explanation](./AI_BACKEND.md) and [local reminder simulation](./EXPIRY_REMINDER_REHEARSAL.md). Both are off by default. Agent/MCP tools, external reminder delivery, MFA, organization RBAC, recovery keys, and client-side zero-knowledge open remain outside the MVP.

Manager sends `Cache-Control: no-store` with successful registration and login responses to keep bearer tokens out of compliant browser and intermediary HTTP caches. The packaged Compose smoke checks both headers. Risks from JavaScript reading the bearer remain tracked under T02.

Registration validates email syntax and the normalized address length before creating an account. Sign-in rejects missing credentials with the generic authentication response. See T01 for the remaining account enumeration and rate-limit considerations.

New Manager accounts require at least 15 Unicode code points in the master password, following the minimum for password-only sign-in. Existing accounts keep their current passwords. The Vault item password policy applies separately. The account policy selection described in [ADR-0011](ADR-0011-account-password-policy.md) governs authenticated tools and new or replaced Vault passwords. Account owners can choose custom requirements within bounded length and category settings. This does not weaken master-password registration requirements or change existing envelopes. Policy settings are plaintext account metadata, are served with no-store, and are never supplied to the built-in-policy AI explanation. Only the existing bounded repeated-character pattern is accepted; users cannot supply arbitrary regular expressions. [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html#passwordver)

The rate limiter uses Spring's application-relative path matching and counts requests by configured route and resolved client. This keeps throttling consistent when Keynest runs under a servlet context path. The deployment limits under T09 and T13 still apply.

The public password verification tool checks the Argon2id format and bounds the requested work before calling the native verifier. The bound is per request. See T13 for the remaining request-size and concurrency considerations.

Evidence reviewed for #43:

- application configuration, controllers, services, repositories, filters, and crypto-library paths at the snapshot above;
- the accepted ADRs and security regression tests landed through PR #80;
- a source scan for logger use, console output, stack-trace printing, request/response logging, and sensitive field names; the only console call found is Angular's bootstrap-failure `console.error`;
- rate-limit path coverage and proxy-identity behavior;
- the PostgreSQL migration and legacy-schema verification added by #79/#80.

## November 23 release boundary refresh

This update covers the source state `d065faf2515fc7b7ea8ee22eda2ea539b05a9418` and approved 0.1.0 version/documentation packaging. It preserves the original snapshot and does not claim a new independent security audit. Applicable accepted contracts now include account policy (ADR-0011), the portable container (ADR-0012), authenticated export (ADR-0013), receiving-key restore (ADR-0014), single-use authenticated transport (ADR-0015) and reviewed conflicts (ADR-0016).

Export uses owner-scoped bounded snapshots, immediate account-password verification, a separately entered backup passphrase, authenticated encryption and no-store responses. Restore authenticates and bounds the file before using its payload, derives the receiving owner from the session and reseals writes with receiving key/item context. Source identities do not grant authority. Identical/matched items default to Ignore; replacements require immutable reviewed target choices, fresh account authentication and explicit acknowledgment. Parent-account locking, state rechecks and atomic transactions coordinate application writers. See [ADR-0016](ADR-0016-restore-conflict-resolution.md) for bounds, concurrency and compatibility.

Restore plans are limited to 64 per process, expire after five minutes and store digests/bindings and decisions rather than files or passwords. Preview/apply must reach the same process. Restart, expiry or changed receiving state requires a new review. Lost responses can leave an outcome uncertain; unsubscribing does not undo a server commit and the client never automatically retries apply. Native files require the backup passphrase and do not replace full PostgreSQL/retained-key recovery.

The public homepage tools operate on transient browser samples. Workspace routes are guarded, while API ownership/session checks remain the authorization authority. Browser cleanup is best effort; JavaScript strings and privileged browser/process memory cannot be reliably erased. No AI/agent access to Vault export/restore is added.

The November 23 logging scan covered production Java and Web TypeScript. The Java logger sites are `SecurityEventLogger` (fixed action/outcome/status and generated event ID/time) and the opt-in console reminder adapter (fixed simulation text). Web retains its bootstrap-failure `console.error`. No `System.out/err` or `printStackTrace` call was found in these production paths. This source scan does not verify external proxy/APM capture or arbitrary runtime logging overrides.

[Exact application-SHA CI](https://github.com/JordyNicholas/Keynest/actions/runs/45213003321) and the [maintainer's Chrome/Ubuntu and retained-key recovery report](MVP_RELEASE_EVIDENCE.md#november-23-maintainer-verification--native-restore-and-core-release-paths) provide separate automated/human evidence. Final source-SHA CI remains required before publication. The maintainer recorded release GO for the verified scope and documented limitations; public-host TLS/ingress verification, live BYOK, third-party migration, registry publication, SBOM/signing and independent auditing are not claimed by this source release.

## System and trust boundaries

```mermaid
flowchart TD
  browser["Manager Web\nUntrusted browser"] -->|"HTTPS + bearer"| edge["Reverse proxy / TLS\nDeployment boundary"]
  edge --> api["Manager API\nTrusted process"]
  api --> libs["Keynest libraries\nIn-process"]
  api --> db["PostgreSQL\nEnvelopes + metadata"]
  operator["Operator\nConfig + backups"] --> edge
  operator --> api
  operator --> db
```

The API and libraries are one trust zone. Compromise of the API process exposes configured key material and the server-side open path. Encryption at rest does not defend against that compromise.

| Boundary | Required assumption | Failure consequence |
| --- | --- | --- |
| Browser → edge/API | TLS terminates only at an operator-controlled endpoint; allowed origins are exact | Bearer or revealed-secret theft, response tampering |
| Direct peer → API | Forwarding headers are ignored unless the direct peer is in an explicit trusted CIDR | Rate-limit identity spoofing |
| API → database | Private authenticated network; least-privileged DB credential; backups protected | Envelopes, account hashes, session digests, and sensitive metadata disclosed or modified |
| Operator → runtime | Secrets supplied outside source control; demo values never used for deployment | Universal vault disclosure or service impersonation |
| API → optional provider | Read-only ephemeral BYOK explanation is disabled by default; fixed-destination adapter and ADR-0010 bound opt-in processing | Third-party policy/provider-credential processing if enabled; live-provider evidence remains pending |

## Security objectives

1. Plaintext vault passwords, master passwords, raw session bearers, vault keys, and future provider credentials never appear in durable storage, logs, telemetry, prompts, or model output.
2. An authenticated user can access only their own vault rows; existence of another user's row is not disclosed.
3. Seal v1 integrity, algorithm, key ID, and canonical AAD bindings fail closed when modified.
4. Authentication and reveal endpoints minimize brute-force, caching, browser-persistence, and proxy-spoofing exposure.
5. Schema changes and key-dependent data are recoverable through reviewed, tested migrations and operator backups.
6. Deterministic server code—not a model, client, or proxy header—decides authorization and policy enforcement.

Availability against a large distributed denial-of-service attack, protection from a malicious host/operator, zero knowledge, hardware-backed key custody, and organization-grade isolation are not current guarantees.

## Assets and data classification

| Asset | Classification | Storage / handling |
| --- | --- | --- |
| Master password and candidate passwords | Critical secret | Request memory only; Argon2id hash persisted for the master password |
| Vault plaintext | Critical secret | Created/opened in API memory; AES-256-GCM envelope persisted |
| Vault key and demo key | Critical secret | Runtime configuration/process memory; never database state |
| Raw session bearer | High secret | Returned once and held in browser `sessionStorage`; never persisted server-side |
| Session digest and password hash | High security data | Database; not authentication responses or logs |
| Envelope bytes and backups | High security data | Database/backup; confidentiality depends on separation from key material |
| Email, label, URL, username, policy, timestamps | Sensitive metadata | Currently plaintext in database and API responses scoped to the owner |
| IP address and actor/resource identifiers | Sensitive telemetry | Use only when operationally necessary and subject to retention controls |

## Threat register

Risk describes the remaining release concern after the listed implemented controls. `Open` means a documented control or operational verification is still required; it does not mean the scenario is currently exploitable.

| ID | Threat | Implemented controls | Residual risk / required action | Risk |
| --- | --- | --- | --- | --- |
| T01 | Login brute force and account enumeration | Argon2id verification; generic login failure; bounded per-identity login/register limiter | Registration still distinguishes an existing email; limiter is per JVM. Add edge/shared enforcement for multi-replica deployment and decide enumeration semantics | Medium, open |
| T02 | Bearer theft through XSS or malicious dependency | 256-bit opaque bearer; tab-scoped `sessionStorage`; seven-day expiry; logout revocation; legacy `localStorage` cleared | JavaScript can read the bearer. Add CSP/dependency hardening and reassess HttpOnly cookies with CSRF/CORS before public Internet deployment | High, open |
| T03 | Database disclosure yields active sessions | Only SHA-256 token digests are stored; raw bearers have 256 bits of entropy | A live bearer stolen elsewhere remains usable until logout/expiry; expired-session cleanup is not implemented | Low |
| T04 | Cross-tenant vault access or existence leak | Owner-scoped item queries and foreign keys; two-user negatives cover CRUD/reveal and bounded export/restore; misses return 404 | Continue isolation checks for future notification, agent and admin paths | Low |
| T05 | Envelope substitution, tampering, or wrong-context open | AES-256-GCM; versioned owner/item/policy AAD for new and resealed rows; unknown AAD versions fail closed; golden/negative tests | Legacy null-version rows bind only policy ID until password update/reseal | Medium, open migration debt |
| T06 | API process or host compromise exposes every vault item | Key is separate from DB; deployed profiles reject missing/demo key; buffers are cleared best-effort | One server-wide key and server-side open make process compromise critical. KeyProvider/per-user wrapped DEKs require a future ADR and migration | High, accepted MVP limitation |
| T07 | Database or backup disclosure exposes metadata | Secrets remain envelopes; password hashes use Argon2id; token digests are not bearers | Labels, URLs, usernames, emails, timing, and policy metadata are plaintext. Encrypt/minimize metadata only through a versioned design; protect backups separately from keys | Medium, open |
| T08 | Demo key or demo decrypt endpoint reaches production | The standalone API binds to loopback by default. The Docker profile has no key default and disables tool seal/open by default. `prod` and `production` reject the demo vault key | Local Compose uses public demo credentials and keys. Verify active profiles, host port bindings, and ingress before deploying | Medium, operational |
| T09 | Forged forwarding headers bypass throttling or exhaust limiter memory | Direct peer used by default; explicit trusted CIDRs; right-to-left hop resolution; header/hop/map bounds | Per-instance fixed windows are not distributed and may permit aggregate abuse; trusted proxy ranges can be misconfigured | Medium, open |
| T10 | Public tools become password oracle or secret-ingestion surface | Validate, hash-password, and verify-password are rate-limited. Demo seal/open are gated. No application request logging | Validate/hash/verify accept secret candidates and generate returns a secret. Document intended exposure, bound request sizes, and consider authentication or deployment-level restriction | Medium, open |
| T11 | Logs, traces, errors, or support artifacts leak secrets | Fixed-field security outcomes and opt-in fixed-text reminder simulation; no payload logging found in the November 23 production-source scan; Angular reports bootstrap failure | Framework debug logging, proxy access logs, APM auto-capture, heap dumps, or future code can reintroduce leakage. Enforce the logging rules below and review every sensitive PR | High, open process control |
| T12 | Migration or rollback corrupts/loses data | Flyway checksummed migrations; Hibernate validation; H2 legacy-upgrade and PostgreSQL 16 integration tests; backup/preflight/single-instance runbook | V2 is not application-only reversible; untested operator backups or loss of the vault key make envelopes unrecoverable. November 23 local retained-key recovery passed; each operator must still rehearse its own recovery | High, operational |
| T13 | Oversized/malformed requests or expensive crypto cause denial of service | Selected auth/tools endpoints are rate-limited. HTTP verification bounds per-request work. Manager shares a configurable per-instance limit across Argon2 operations | Request-size limits, timeouts and aggregate capacity across replicas require deployment controls. Parsing paths can still be resource intensive | Medium, open |
| T14 | CORS/TLS/proxy deployment error exposes API | Exact configured CORS origins; bearer sent in `Authorization`; no cookie-auth CSRF surface; forwarding headers untrusted by default | TLS is external; wildcard headers are allowed for configured origins; origin lists and proxy topology need deployment verification | Medium, operational |
| T15 | Vulnerable dependency or build workflow compromises release | Java/Angular builds run in CI; version policy is documented | Dependency update automation, CodeQL/SAST, SBOM, artifact signing/provenance, and pinned third-party actions are not yet release evidence | High, open release hardening |
| T16 | Future AI/provider/agent feature leaks secrets or grants model authority | ADR-0010 limits the optional runtime to one read-only explanation of a built-in policy with an ephemeral provider key; agent/MCP runtime is deferred | Live-provider verification remains pending. Agent identity, scopes and approval flows require separate design and tests | High, gated |
| T17 | Future notifications copy plaintext into an outbox/provider | The outbox stores item references and expiry timestamps. The opt-in console adapter emits a fixed simulation event | External delivery remains deferred pending verified recipient consent, redaction, and transport review | Medium, gated |
| T18 | Native backup theft or offline passphrase guessing | Authenticated passphrase-protected container, bounded Argon2id parameters and strict decoding | File possession permits offline guessing; choose a strong distinct passphrase and retain it separately. Passphrase loss is unrecoverable | High, custody limitation |
| T19 | Wrong-target, stale or replayed restore changes Vault | Safe Ignore defaults, owner/session/file/state-bound single-use plans, explicit target/acknowledgment, parent locks and atomic writes | Plans are process-local; direct SQL writers are outside coordination. Lost commit acknowledgments require refresh/review, never automatic replay | Medium, operational |

## Logging and diagnostic-data policy

Container hardening in #90 runs the API as UID/GID 10001 with read-only root, bounded writable temporary storage, dropped capabilities, no-new-privileges and CPU/memory/PID/core limits in Compose. Local ports bind to loopback. Image defaults disable JVM attach, automatic OOM heap/core dumps and fatal-error files. Privileged host/process memory access and deliberate operator overrides remain outside these controls; `/tmp` allows native-library execution for Argon2/JNA. See [Compose runtime controls](./COMPOSE.md#api-runtime-controls).

The health hardening in #82 opts in only read-only Actuator health access, hides components/details, disables discovery and JMX exposure, and separates process liveness from database readiness. Integration tests cover database failure/recovery and diagnostic endpoint denial. This limits HTTP diagnostic disclosure; operator-supplied configuration and telemetry still require deployment verification.

Web deployment configuration in #85 replaces the cloud-host placeholder with a same-origin default and a validated public build variable. The destination receives bearer tokens and vault requests; operators must control its origin. Validation rejects credentials, query/fragment data, protocol-relative URLs and non-loopback HTTP, but cannot establish that an HTTPS host is trustworthy. Rebuild after changing destinations and verify routing/CORS before sign-in.

The #43 Java source review found no `Logger`, `@Slf4j`, `System.out/err`, `printStackTrace`, application request/response logging, or code that deliberately records passwords, plaintext, envelopes, keys, authorization headers, or tokens. Manager Web retains Angular's generated bootstrap-failure `console.error`; it does not log HTTP payloads. Spring/Flyway/Hibernate startup logs still exist, and deployment infrastructure may add access logs or telemetry outside this repository.

Never record:

- request or response bodies on auth, tools, vault, provider, agent, export, or notification routes;
- `Authorization`, cookies, raw or digested session tokens, password hashes, password candidates, generated secrets, plaintext, envelope bytes/Base64, vault keys, provider credentials, or recovery material;
- URL query strings when they may contain user input;
- email, vault label, target URL, username, or full IP address by default;
- decrypted heap dumps, database snapshots, or failing fixtures in CI artifacts and PR comments.

Permitted security events use an allowlist: stable event name, UTC timestamp, outcome/reason code, route template, deployment instance, and opaque actor/resource identifiers only when necessary. Do not use record/object `toString()` on sensitive request, entity, envelope, or configuration types. Correlation IDs must be random and must not reuse credentials or user data.

Production operators must keep HTTP body/header capture and framework request-detail logging disabled, omit query strings and sensitive headers from proxy access logs, restrict actuator exposure, protect diagnostics with separate access controls, disable or tightly control heap dumps, define retention, and test redaction in the actual telemetry stack. A future audit implementation requires its own schema and tests; this review does not claim a durable audit log.

## Verification before public release

- Run Java 21/25, Angular, PostgreSQL, migration, tenant-isolation, AAD, session, reveal-cache, and proxy tests on the release commit.
- Re-run the logging scan and review every new log/metric/trace field introduced after this snapshot.
- Exercise login abuse through the real reverse proxy and confirm the resolved client identity and shared/edge limits.
- Verify TLS, exact CORS origins, inactive demo tools, non-demo vault key, restricted actuator endpoints, request-size/time limits, and sanitized access logs in the release topology.
- Restore a database backup and its separately preserved vault key, then reveal a pre-backup item and record only pass/fail evidence.
- Scan dependencies and container images; produce an SBOM and resolve release-blocking findings.
- Verify AI-disabled core journeys and default-off reminder settings. Keep external email and agent tools outside the MVP. If live BYOK is enabled for release, attach reviewed egress, redaction and failure-path evidence under ADR-0010.

## Residual-risk decisions

The maintainer must explicitly accept or resolve T02, T06, T11, T12, T15, and any newly introduced high-risk item before a public release. T16 and T17 are feature gates: absence of their design and tests means those features do not ship. Medium operational risks require documented deployment checks and honest known-limitations notes.

Any change to encryption/AAD, key custody, authentication/session transport, authorization, reveal/export, logging/telemetry, migrations/backups, provider egress, or agent capabilities must update this model or state why its assumptions and risk ratings remain valid.
