# Database migration runbook

Keynest Manager uses Flyway migrations from `apps/manager-api/src/main/resources/db/migration`. Hibernate validates the resulting schema and never creates or updates it.

## Fresh database

Start Manager API with an empty H2 or PostgreSQL database. Flyway executes every migration in order before JPA starts. Startup must fail if migration or Hibernate validation fails; do not bypass either check.

## First upgrade from a pre-Flyway installation

This upgrade changes the physical session column from `token` to `token_digest` and adds referential constraints. Stored digest values and vault envelopes are not rewritten.

1. Stop every Manager API writer.
2. Back up the PostgreSQL database using the platform's verified backup/export mechanism.
3. Back up the exact `KEYNEST_VAULT_KEY_HEX` and `KEYNEST_VAULT_KEY_ID` through the operator's secret-management process. Do not put them in the database dump, repository, logs or support messages.
4. Confirm the target database is the intended Keynest database and contains `users`, `auth_sessions` and `vault_items`.
5. Check for orphaned rows; both results must be zero:

   ```sql
   SELECT COUNT(*)
   FROM auth_sessions s LEFT JOIN users u ON u.id = s.user_id
   WHERE u.id IS NULL;

   SELECT COUNT(*)
   FROM vault_items v LEFT JOIN users u ON u.id = v.user_id
   WHERE u.id IS NULL;
   ```

6. Start exactly one upgraded API instance. Flyway baselines the non-empty schema at V1 and applies all subsequent migrations. The current schema ends at V9; the V8 activation transition deliberately pauses new signup while retaining existing-owner access. See [account activation](account-activation.md) before upgrading. Do not start the rest of the fleet until this instance is healthy.
7. Verify migration history and column state:

   ```sql
   SELECT installed_rank, version, description, success
   FROM flyway_schema_history
   ORDER BY installed_rank;

   SELECT column_name
   FROM information_schema.columns
   WHERE table_name = 'auth_sessions'
     AND column_name IN ('token', 'token_digest');
   ```

   Expected: successful V1 baseline and V2 through V9 migration records; `token_digest` exists and `token` does not.
8. Verify that direct signup returns the documented fixed HTTP 410 and an existing owner can sign in, list, create and reveal with non-production test data. Fresh installs cannot onboard owners during this staged transition. Restore normal instance capacity only after the matching API/Web transition checks pass.

If any preflight result is non-zero or migration fails, keep writers stopped and investigate. Do not delete orphaned or encrypted rows merely to make the migration pass.

V3 adds the expiry outbox, V4 adds dispatch claim and retry fields, and V5 adds recipient verification and consent fields. V6 adds nullable account password policy configuration. Existing accounts retain the built-in policy. Database migration does not enable external delivery. V7 adds the session expiry index; see [bounded cleanup, operator settings and V6-compatible rollback](../security/controls/session-cleanup.md). Index creation can block session writes, so preserve the stopped-writer rollout and assess large installations before upgrading.

V8 adds activation eligibility and grandfathers only existing rows without claiming mailbox proof; V9 adds initially empty bounded verification state. The [activation transition and rollback](account-activation.md) takes precedence over historical V7/V2 compatibility notes below. Internal mail and application-use-case slices add no automatic sender or public activation path.

## Rollback

Current V8/V9 applications retain signup retirement and owner eligibility on rollback. Do not deploy a pre-V8 signup handler, remove constraints/columns/history, or restore an old database merely to reopen signup. Retain the matching key and use a compatible forward fix. Restoring V9+ state requires the authority invalidation below before traffic/delivery resumes.

At the historical V7-only boundary, V7 adds only an index and is compatible with the preceding V6 application; the current V8/V9 rules above take precedence. Leave the index and migration history in place on an application rollback; deleted expired sessions were already unusable and must not be resurrected. The older V2 boundary below remains incompatible.

At the historical V2 boundary, a pre-V2 binary expects the old column name, so application-only rollback across that boundary is unsafe. Stop upgraded instances, restore the pre-upgrade database backup, restore the matching vault-key configuration if it changed independently, then start the previous version. Preserve the failed database separately for diagnosis.

## Restore authority invalidation (V9 and later)

Restoring an older database can revive a consumed verification link and previously revoked bearers. With **all API and worker instances stopped**, restore the intended database and its matching installation key through the normal verified mechanism. Before reopening any traffic or future delivery, run the reviewed [invalidation SQL](../../apps/manager-api/src/main/resources/recovery/invalidate-restored-authority.sql) against that intended V9+ database using the operator's approved libpq service/secret mechanism:

```bash
PGSERVICE=keynest-recovery psql --set=ON_ERROR_STOP=1 --file=apps/manager-api/src/main/resources/recovery/invalidate-restored-authority.sql
```

`keynest-recovery` is a locally configured service name, not a repository credential or a command to run against an arbitrary database. Verify its destination and run from the repository root (or use the reviewed SQL from the matching source release). This explicit transaction deletes **all** pending verification generations/jobs and **all** restored sessions. Every device must sign in again. It preserves users/password hashes/verification history, Vault data/keys and retained rolling quota events. It performs no schema change or account creation and is not loaded automatically on startup or migration.

The script takes exclusive locks with a five-second lock timeout and thirty-second statement timeout. Any error must abort through `ON_ERROR_STOP`; keep writers/traffic stopped, retain the failed restore separately, investigate and repeat the reviewed recovery procedure. Never bypass failed invalidation or hand-edit verification timestamps. An operator recovery is not an opportunity to reset a user's password or replace the installation key.

After successful commit, verify zero rows in `verification_challenges`, `verification_delivery_jobs` and `auth_sessions`, then start the matching API/Web. Confirm old bearer/link refusal, fresh ordinary password sign-in and readable retained Vault data before reopening the fleet. No mail probe or public signup is enabled by this script. An actual deployment requires its own stopped-writer/key-retention rehearsal; the CI evidence below is confined to a disposable PostgreSQL database.

## Adding migrations

- Add a new immutable `V<version>__<description>.sql`; never edit an applied migration.
- Make data compatibility, locking, failure behavior, rollback and backup requirements explicit in the PR.
- Test fresh H2, legacy upgrade when relevant, and PostgreSQL for dialect-sensitive changes.
- Do not mix Hibernate schema mutation, `schema.sql` or manual production DDL with Flyway.

## Automated backup/restore rehearsal

`PostgresRestoreIntegrationTest` starts PostgreSQL 16 in a disposable container and Manager against a fresh source database with an ephemeral vault key. It creates an account, session and generated vault item, closes Manager to stop writers, runs `pg_dump --format=custom`, creates a separate restore database, and runs `pg_restore --exit-on-error`. Manager then starts against the restored database with the retained key and validates Flyway history and JPA mappings.

The test changes the account password and replaces prior sessions before backup, preserving envelope bytes. It adds a pending digest-only proof to the backup, then consumes it in the source after backup. In a non-Web restored context it checks row counts, retained-key decryption, old-password rejection and normal sign-in, and characterizes that database rollback revives the older pending proof. All contexts then close; the exact reviewed recovery SQL invalidates pending generations/jobs and every restored bearer. A second restart must reject old proof/bearer, accept fresh ordinary sign-in with the retained password and decrypt the unchanged Vault envelope. Secret comparisons report only pass/fail. The dump stays inside the disposable container and is never uploaded; no operator-supplied database is contacted. This exercises the PostgreSQL recovery path, not arbitrary H2 recovery or an external anti-rollback ledger.

Run with Java 21+, Maven and Docker:

```bash
mvn -pl apps/manager-api -am -Dtest=PostgresRestoreIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The container test skips when Docker is unavailable. CI must execute it and record zero skips before using it as evidence. This proves the logical dump/application restore path on PostgreSQL 16; it does not prove an operator's backup storage, key escrow, TLS/network settings, packaged container deployment, point-in-time recovery or recovery-time objective. Rehearse the actual deployment separately using the first-upgrade/rollback procedure above, and retain only sanitized outcomes as release evidence.
