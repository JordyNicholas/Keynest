# Runtime operations

## Manager health probes

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

The tools `/api/v1/tools/health` endpoint remains a compatibility/demo response and does not check the database. Use the readiness endpoint for traffic routing. See the [Compose options](local-setup.md) and verify the chosen probe routing and orchestration settings in the target environment.

`HealthEndpointIntegrationTest` exercises healthy responses, datasource failure/recovery, liveness independence, and denial of diagnostic endpoints. The outage is injected into the datasource after application startup; a PostgreSQL network-outage rehearsal remains deployment evidence to collect. See [database migrations](database-migrations.md) and the [threat model](../security/threat-model.md) for backup/restore and release checks.

## Expired-session storage hygiene

The API removes at most 500 expired sessions per five-minute scheduled tick by default. Active sessions remain valid. See [worker bounds, failure behavior and V7 upgrade](../security/controls/session-cleanup.md) before tuning the batch or delay; cleanup failure does not permit expired authentication. Owner-wide sign-out and account password change are described below.

## Owner-wide sign-out

Account settings → Sign out all devices requires fresh current-password verification and ends the current session too. It preserves Vault data and permits later valid sign-in. Review [database ordering, lost-response handling and rollback](../architecture/decisions/ADR-0017-owner-wide-session-revocation.md); revocation does not retract responses or retrospectively cancel work already authorized. Email verification, MFA and password recovery remain separate designs.

## Changing the account password

Account settings → Change password verifies the current password, replaces the hash and every prior session atomically, and issues a fresh bearer for the current browser. Vault keys/data and export passphrases stay unchanged. On an uncertain response, sign in again with the new password first; the browser does not replay the request. See [ordering, failure/rollback and retained-key recovery](../architecture/decisions/ADR-0018-account-password-change.md). Restoring an older database may restore an older password/session state; verify that state after recovery and deliberately revoke/change again when required.

## Saturation and admission failures

Hashing slots and bounded client/route and login-identifier tables protect process resources, but can deny legitimate users when occupied/full. A 503 at password-work admission and a 429 at a limiter are distinct from probe health; readiness can remain UP. See [availability controls](../security/controls/availability.md) for admission ordering, expiry/recovery, safe tuning, restart/replica limits and shared ingress requirements. Do not automatically replay an uncertain mutation after a connection failure. This guidance does not accept the open T01/T09/T13 residual risks.

## Rate-limit configuration

For selected POST routes, the default client/route budget is 30 requests per 60 seconds. Overflow returns HTTP 429 with `Retry-After`. See the [application route set](../../apps/manager-api/src/main/java/dev/keynest/manager/api/ApiPaths.java) for the exact routes. A separate [login identifier policy](../security/controls/login-throttling.md) applies to admitted sign-in attempts on unreleased `main`.

Configure per-instance limits with `KEYNEST_RATE_LIMIT_LIMIT`, `KEYNEST_RATE_LIMIT_WINDOW_SECONDS`, `KEYNEST_RATE_LIMIT_MAX_ENTRIES`, and `KEYNEST_RATE_LIMIT_MAX_FORWARDED_FOR_ENTRIES`. The API uses the direct socket peer by default. Behind a reverse proxy, set `KEYNEST_RATE_LIMIT_TRUSTED_PROXIES` to the proxy's documented addresses or CIDRs. Identity resolution walks from the nearest trusted hop to the first untrusted address. See the [deployment guide](deployment.md) for ingress requirements.
