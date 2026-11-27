# Bounded expired-session cleanup

Roadmap M1 H4, issue #305. This is the first lifecycle slice; revoke-all and changing the account password remain separate work. Authentication already denies expired rows whether or not cleanup succeeds.

## Execution and limits

Every Manager instance registers one fixed-delay task independently of reminder and AI flags. Both the initial delay and the delay after completion default to 300 seconds. Each tick calls a separate transactional service through its Spring proxy. At one UTC clock cutoff it selects only IDs with `expires_at <= cutoff`, ordered by expiry then ID, in a first page of at most 500. It conditionally deletes those IDs only if they remain expired at that same cutoff. No user, digest or Vault data is hydrated for this work. An empty page performs no delete. One tick runs one batch, without a backlog-draining loop.

| Environment variable | Default | Accepted bounds |
| --- | --- | --- |
| `KEYNEST_SESSION_CLEANUP_BATCH_SIZE` | 500 | 1–5000 rows |
| `KEYNEST_SESSION_CLEANUP_DELAY_SECONDS` | 300 | 10–86400 seconds |

Both supplied Compose definitions forward these settings; standalone Spring properties use `keynest.session-cleanup.batch-size` and `delay-seconds`. Invalid configuration fails startup. There is no cleanup-disable switch: a failed worker does not change authentication's expiry checks. A large backlog drains across successive runs; defaults permit at most 500 deletions per completed five-minute tick per instance. Operators can adjust within the bounds after assessing database load.

The transaction requests a ten-second timeout. Transaction/query timeout enforcement and driver cancellation are database/driver dependent; datasource acquisition, network stalls and transaction completion are not a hard end-to-end deadline. Fixed delay prevents overlapping invocations of this task within one application context. Multiple replicas can select the same rows; conditional deletes are idempotent and a concurrently deleted or extended row is skipped. This is not a distributed leader or exactly-once job. A database contention/connection failure rolls the batch back and the next tick can retry. The scheduler emits one fixed warning without the exception, SQL, identifiers or token values; there is no cleanup endpoint or per-user metric.

The task uses the application's existing UTC `Clock` bean. Expiry authentication still performs its existing fresh server-time check. Cleanup is storage hygiene, not a new expiry, revocation or authorization policy. Seven-day sessions, login/logout, digest storage, browser transport, owner checks and fresh export/restore password verification are unchanged. A compromised database/operator can still modify sessions; cleanup does not remediate that trust boundary. Wall-clock errors affect expiry as before.

## Migration and rollback

Flyway V7 adds the non-unique `(expires_at, id)` index without rewriting rows or changing keys/envelopes. This supports bounded ordered candidate queries, but does not promise fixed scan latency. Normal transactional index creation may block session writes; use the [controlled stopped-writer upgrade](../../DATABASE_MIGRATIONS.md), a verified database backup and the separately retained matching Vault key. Assess index build time/free space on the real installation before upgrading a large table. Do not edit an applied migration or bypass validation.

The preceding V6 application is compatible with the added index; an application rollback can leave V7 history/index in place. Already removed expired sessions stay removed (they were already unusable). Do not rewrite Flyway history or resurrect sessions to roll back cleanup. Rolling back across V2 or other incompatible migrations still requires the documented matching backup procedure.

## Verification scope

Deterministic unit tests cover one cutoff, page bounds, no empty delete, task registration/delays with reminders disabled, invalid settings and sanitized retry behavior. The same H2/PostgreSQL integration contract covers the inclusive expiry boundary, multi-batch progression, preserved active authentication, stale selection after extension/deletion and competing independent deletion transactions. A populated PostgreSQL V6→V7 test checks index creation and unchanged session/envelope values; existing fresh/legacy migrations and retained-key dump/restore assertions target V7. Container tests require Docker and must execute with zero skips in CI before being cited as PostgreSQL evidence. These synthetic tests do not establish an operator's backup retention, production index-build time or performance under sustained attack.
