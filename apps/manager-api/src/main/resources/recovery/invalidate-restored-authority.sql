-- Explicit operator recovery only. Stop ALL API/worker instances before running.
-- Retain the restored database and its matching installation key; select the intended V9+ database.
-- psql must use ON_ERROR_STOP=1. This is NOT a startup/Flyway script.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
LOCK TABLE users, auth_sessions, verification_gate, verification_challenges, verification_delivery_jobs
  IN ACCESS EXCLUSIVE MODE;
-- Cascades reference-only jobs; keep rolling quota events and existing account/Vault state.
DELETE FROM verification_challenges;
-- Revoke every restored bearer, including initiating legacy-verification sessions.
DELETE FROM auth_sessions;
COMMIT;
