# Account activation transition

**Unreleased foundation merged through #337 / #336, based on accepted [ADR-0021](../architecture/decisions/ADR-0021-verified-email-activation.md).** This guide does not describe v0.1.1's published behavior or claim a working mail flow.

The first slice retires direct signup: `POST /api/v1/auth/register` returns fixed HTTP 410 with `code: REGISTRATION_REQUIRES_VERIFICATION`, without creating an account/session or inspecting the submitted address. The Web signup entry explains the temporary pause and offers existing-owner sign-in. There is no allow-unverified switch, bootstrap owner endpoint, or production fixture API. **Fresh installations cannot onboard owners until the later activation implementation ships.** Operators needing new signups should evaluate this transition before upgrading; the foundation is not a release/publication decision.

## Existing owners and upgrade

V8 adds nullable verification timestamp/delivery-address metadata and a false-by-default legacy eligibility flag. Only rows present during migration receive the legacy flag; none is labeled email-verified. A database constraint rejects a new unverified/nonlegacy account. Login/session resolution also rejects ineligible state. Existing passwords, session digests/expiry, Vault envelopes, identifiers and installation keys are retained. A legacy owner can continue sign-in, reveal, export/restore and account operations with current password proof; Account settings shows an informational verification-pending notice. Verification request/confirmation and SMTP configuration are not available in this slice.

Before applying V8, take and retain the normal database backup and matching installation key. Pause registration and stop old application writers during migration; never load-balance old/new signup handlers during the transition. Deploy matching API/Web, verify old signup refusal, an existing owner's password/session/Vault access and `emailVerified: false`, then retain the signup pause until reviewed activation is ready. Do not hand-edit existing owners' verification timestamps to bypass the later flow or delete data to repair startup.

## Internal verification state

V9 merged through #339/#340 adds empty internal challenge, quota and reference-only delivery-job tables. It changes no existing owner/session/Vault data and adds no public endpoint, sender or activation method. The [state contract](../architecture/verification-state.md) documents shared database limits, one committed claim per generation, ten-minute digest-only tokens and bounded scheduled cleanup. No worker sends mail automatically. The [internal SMTP/mailbox adapter](verification-mail.md) merged through #341/#342 adds optional installation settings and isolated TLS tests; public activation is still unavailable.

Keep SQL/bind-parameter tracing off: recipient/session metadata is sensitive even without raw tokens. Healthy running maintenance removes expired state in bounded passes; offline databases, failed maintenance and backups require operator retention controls. Do not treat restoring this database as token revocation: restoration can revive consumed state. Before future mail/activation enablement after recovery, pending generations/jobs must be invalidated and restored sessions revoked through a reviewed, rehearsed runbook. That enablement remains #338 work.

## Failure and rollback

Migration failure must fail startup; never disable Flyway/schema validation or fall back to unverified signup. Existing login/Vault requires no mail service. No SMTP settings or credentials are requested for this slice.

Prefer forward-fix with signup paused. A pre-V8 binary has no supported rollback contract: it may reject the migration history, attempt now-invalid direct inserts, or present misleading signup UI. A compatible rollback build must retain the eligibility constraint, existing-owner behavior and retired signup response. Do not drop columns/constraints or restore an old database just to restore the signup button. Intentional database recovery retains the matching installation key and the existing session-revocation/recovery rules. Later challenge/job restore invalidation remains a prerequisite for the mail rollout.

## Disposable verification fixtures

Java test sources create synthetic verified accounts directly for independent auth/Vault tests; they are not packaged in the production application or proof of mailbox delivery. The migration contract separately builds real V7 legacy data, upgrades it, authenticates the retained bearer/short password and decrypts its retained envelope on H2 and PostgreSQL.

Compose/browser tests seed **unverified legacy** records through PostgreSQL inside a deliberately disposable project, then exercise real HTTP sign-in and Vault operations. `scripts/disposable_accounts.py` requires `COMPOSE_PROJECT_NAME` matching `keynest-ci-<digits>-<digits>` or `keynest-hosted-ci-<digits>-<digits>`, one of the two reviewed Compose files, and constrained `example.invalid` fixture addresses. It uses a known test-only password/hash, suppresses SQL/credential output and exposes no endpoint. Never use this script or its known fixture credential for an ordinary deployment. Existing `--disposable` smoke invocations now require that explicit project naming; hosted smoke also selects its hosted Compose file.

CI must verify the full supported lifecycle with those fixtures, both clean/populated migration paths, wrong/unknown/ineligible account rejection, identical retired-signup responses, retained-owner access and Web notices. Browser/Compose/mail results must not be inferred from source tests. Mail delivery/activation, legacy verification requests and action-bound codes remain separate reviewed slices; no T01/H1/M1 gate closes from this foundation alone.

The [application use-case candidate](../architecture/account-verification-use-cases.md) under #343 connects atomic activation, fresh-password/same-session owner verification and an explicit delivery coordinator without public routes or a schedule. Its reviewed recovery SQL/rehearsal is documented in [database recovery](database-migrations.md#restore-authority-invalidation-v9-and-later); no operator database is modified by ordinary startup.
