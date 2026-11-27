# Manager health probes

Manager exposes status-only Actuator health checks on its application port, independent of hosting provider. All profiles inherit these defaults.

| Endpoint | Checks | Use |
| --- | --- | --- |
| `GET /actuator/health/liveness` | Spring application liveness state | Restart only when the application itself is unhealthy |
| `GET /actuator/health/readiness` | Spring readiness state and database connectivity | Remove the instance from traffic while it cannot serve database-backed operations |
| `GET /actuator/health` | Aggregate configured health contributors | General operational health; prefer the explicit groups for orchestration |

Healthy probe responses are HTTP 200 with only `{"status":"UP"}`. The aggregate `/actuator/health` response also lists the public group names `liveness` and `readiness`. A database connectivity failure produces readiness HTTP 503 with only `{"status":"DOWN"}` while liveness stays UP. Readiness recovers when connectivity returns. Application shutdown/startup availability state can also make an instance unready. Flyway or key validation failure prevents successful application startup, so probes cannot report success for an instance that failed to start.

Health details and component names are hidden, including database URLs and exception text. The Actuator discovery page is disabled. Only read access to the health endpoint is enabled; environment, configuration, heap dumps, logger controls, metrics, mappings, migrations, and other diagnostic endpoints are unavailable. JMX endpoint exposure is disabled. Changing these defaults requires a review of authentication, network access, and sensitive data exposure.

Route probes through a private operator/orchestrator path where practical. The endpoints require no session token and reveal only coarse availability; restrict their public routing at the edge when external access is unnecessary. Configure probe timeouts and failure thresholds for the actual database/network latency. These checks are synchronous and depend on datasource connection timeouts; they do not provide an independent timeout or a distributed availability guarantee.

Example local check after startup:

```bash
curl --fail http://localhost:8080/actuator/health/liveness
curl --fail http://localhost:8080/actuator/health/readiness
```

The tools `/api/v1/tools/health` endpoint remains a compatibility/demo response and does not check the database. Use the readiness endpoint for traffic routing. No Compose or cloud healthcheck/restart policy is installed by this change; verify the chosen routing and orchestration settings during the full-stack rehearsal.

`HealthEndpointIntegrationTest` exercises healthy responses, datasource failure/recovery, liveness independence, and denial of diagnostic endpoints. The outage is injected into the datasource after application startup; a PostgreSQL network-outage rehearsal remains deployment evidence to collect. See [database migrations](./DATABASE_MIGRATIONS.md) and the [threat model](./THREAT_MODEL.md) for backup/restore and release checks.

## Expired-session storage hygiene

The API removes at most 500 expired sessions per five-minute scheduled tick by default. Active sessions remain valid. See [worker bounds, failure behavior and V7 upgrade](design/security/SESSION_CLEANUP.md) before tuning the batch or delay; cleanup failure does not permit expired authentication. Owner-wide sign-out and account password change are described below.

## Owner-wide sign-out

Account settings → Sign out all devices requires fresh current-password verification and ends the current session too. It preserves Vault data and permits later valid sign-in. Review [database ordering, lost-response handling and rollback](ADR-0017-owner-wide-session-revocation.md); revocation does not retract responses or retrospectively cancel work already authorized. Email verification, MFA and password recovery remain separate designs.

## Changing the account password

Account settings → Change password verifies the current password, replaces the hash and every prior session atomically, and issues a fresh bearer for the current browser. Vault keys/data and export passphrases stay unchanged. On an uncertain response, sign in again with the new password first; the browser does not replay the request. See [ordering, failure/rollback and retained-key recovery](ADR-0018-account-password-change.md). Restoring an older database may restore an older password/session state; verify that state after recovery and deliberately revoke/change again when required.
