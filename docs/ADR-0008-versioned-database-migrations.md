# ADR 0008 — Versioned database migrations and PostgreSQL verification

## Status

Accepted in maintainer-approved PR #80 on November 11, 2026.

## Context

Manager previously allowed Hibernate `ddl-auto=update` to mutate both local H2 and deployed PostgreSQL schemas. Those implicit changes were not reviewable, reproducible or covered against the production database. Existing installations have the current three tables but no migration history, so adopting a migration tool must handle both fresh databases and non-empty legacy schemas without deleting rows.

The session hardening in #71 deliberately retained the physical `auth_sessions.token` name even though the column now contains SHA-256 digests. A versioned migration is the appropriate place to align that name.

## Decision

1. Flyway is the only runtime schema-mutation mechanism. Hibernate uses `ddl-auto=validate` and fails startup when entity mappings and the migrated schema disagree.
2. V1 describes the current pre-Flyway schema. A fresh empty database executes V1 normally.
3. A non-empty schema without Flyway history is baselined at V1, then executes later migrations. This compatibility behavior is enabled with `baseline-on-migrate`; operators must use it only after the preflight and backup procedure in [Database migrations](./DATABASE_MIGRATIONS.md).
4. V2 renames `auth_sessions.token` to `token_digest`, preserving every stored value. It adds owner lookup indexes and foreign keys from sessions/items to users with cascade-on-user-delete semantics.
5. PostgreSQL 16 Testcontainers coverage verifies a fresh schema plus registration, vault creation and reveal. A separate H2 test constructs the representative legacy schema, baselines it, runs V2 and proves rows survive.
6. Production upgrades stop writers, back up the database and vault key, run one upgraded instance to completion, verify migration state, then restore capacity. Flyway's locking is not a substitute for this controlled first rollout.

## Consequences

- Schema changes become ordered, checksummed and reviewable.
- Existing session digest values remain valid after the column rename. No envelope or key material changes.
- V2 refuses orphaned session/vault rows when it adds foreign keys. That is a fail-safe migration stop; operators must investigate rather than discard rows automatically.
- The previous application expects `auth_sessions.token` and cannot run against a V2 schema. Rollback requires restoring the pre-upgrade database backup before starting the previous application.
- `baseline-on-migrate` can be dangerous when pointed at an unrelated non-empty schema. Database targeting, backups, preflight queries and Hibernate validation remain mandatory operator controls.
- Container-specific verification is skipped only when Docker is unavailable; CI must provide Docker and execute it before merge/release evidence is accepted.

## Verification

- Default H2 Spring contexts migrate then pass Hibernate validation.
- `LegacySchemaMigrationTest` covers the baseline/V2 upgrade and row preservation.
- `PostgresMigrationIntegrationTest` covers PostgreSQL 16 and a core authenticated vault flow.
- CI runs Java 21 and Java 25 plus the existing Angular build.
