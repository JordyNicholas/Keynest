# ADR 0009 — Sanitized HTTP security outcome events

## Status

Proposed for maintainer review in #92.

## Context

Manager has authentication, session and vault controls but no application security-event stream. Logging request objects would expose credentials and metadata. A durable, actor-attributed audit store needs separate retention, transaction, integrity and access decisions; adding a generic payload-bearing audit API now would enlarge the leakage surface.

## Decision

1. Emit best-effort HTTP outcome events through a dedicated logger for a fixed set of synchronous auth/vault routes and methods. A filter wraps the rate limiter so its 429 outcomes are visible.
2. Schema v1 contains only server-generated event UUID, UTC `Instant`, enumerated action/outcome and HTTP status. It accepts no arbitrary text, request-derived identifier, path, query, header, body, exception, IP, email, label, URL, username, token/digest, hash or envelope.
3. HTTP 2xx maps to SUCCESS, 4xx to REJECTED, 5xx to ERROR, and other returned statuses to OTHER. An exception escaping the chain produces an ERROR classification with status 500 while propagating unchanged. This is a filter observation, not a guarantee of the final error-dispatch response or a committed domain transaction.
4. Unknown paths, unsupported methods, public tools/health routes and async-started requests are omitted. New routes must opt in with a fixed action and tests; async controllers need completion-aware design.
5. A logging RuntimeException cannot replace the application response or grant/deny access. Events may be lost, reordered by collectors, duplicated by retries or blocked by a configured logging backend. There is no durable delivery, actor attribution, hash chain, retention enforcement or audit-export API.
6. Use the platform's normal logging transport; no mandatory collector, cloud provider, database schema or AI service is introduced. Operators control access, rotation, retention, collector egress and logger level. Disable with `logging.level.dev.keynest.manager.security.SecurityEventLogger=OFF` when required; loss of visibility must be an explicit operator decision.

## Consequences and limits

The stream supports aggregate auth/vault failure investigation without storing user-supplied data. It cannot answer which user changed a resource or prove the change committed. It is not a compliance audit trail. A future attributed/durable audit feature requires a new ADR for identities/resources, retention, transaction coupling, replay/integrity, authorization and safe export.

The threat model's T11 diagnostic/telemetry risk remains open: these events reduce application payload exposure but cannot sanitize framework logs, arbitrary appenders/MDC, proxy/APM capture, host diagnostics or future code. Never feed the security stream to an AI provider by default. High-volume rejected requests can produce log pressure; deployment rate limits and bounded log retention remain required operational work.

## Verification

Unit tests capture actual logger output and check fixed-action mapping, request/response/exception sentinel exclusion, rejection/error classification, unknown-route omission and logger-failure behavior. A full Spring/MockMvc test checks that real authentication failure and limiter rejection both emit sanitized events. Existing API, PostgreSQL, browser and packaged Compose tests remain required gates.

## Rollback

Revert the filter/logger or disable this logger. No session, schema, envelope or key migration is involved. Disabling/reverting loses new security visibility; it does not change authentication or vault authorization.
