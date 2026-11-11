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

6. Start exactly one upgraded API instance. Flyway baselines the non-empty schema at V1 and applies V2. Do not start the rest of the fleet until this instance is healthy.
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

   Expected: successful V1 baseline and V2 migration records; `token_digest` exists and `token` does not.
8. Exercise register/login, list, create and reveal with non-production test data, then restore normal instance capacity.

If any preflight result is non-zero or migration fails, keep writers stopped and investigate. Do not delete orphaned or encrypted rows merely to make the migration pass.

## Rollback

The previous application version expects the old column name, so application-only rollback is unsafe after V2. Stop upgraded instances, restore the pre-upgrade database backup, restore the matching vault-key configuration if it changed independently, then start the previous version. Preserve the failed database separately for diagnosis.

## Adding migrations

- Add a new immutable `V<version>__<description>.sql`; never edit an applied migration.
- Make data compatibility, locking, failure behavior, rollback and backup requirements explicit in the PR.
- Test fresh H2, legacy upgrade when relevant, and PostgreSQL for dialect-sensitive changes.
- Do not mix Hibernate schema mutation, `schema.sql` or manual production DDL with Flyway.
