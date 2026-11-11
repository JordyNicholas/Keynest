# Manager health probes

Manager exposes status-only Actuator health checks on its application port, independent of hosting provider. All profiles inherit these defaults.

| Endpoint | Checks | Use |
| --- | --- | --- |
| `GET /actuator/health/liveness` | Spring application liveness state | Restart only when the application itself is unhealthy |
| `GET /actuator/health/readiness` | Spring readiness state and database connectivity | Remove the instance from traffic while it cannot serve database-backed operations |
| `GET /actuator/health` | Aggregate configured health contributors | General operational health; prefer the explicit groups for orchestration |

Healthy responses are HTTP 200 with only `{"status":"UP"}`. A database connectivity failure produces readiness HTTP 503 with only `{"status":"DOWN"}` while liveness stays UP. Readiness recovers when connectivity returns. Application shutdown/startup availability state can also make an instance unready. Flyway or key validation failure prevents successful application startup, so probes cannot report success for an instance that failed to start.

Health details and component names are hidden, including database URLs and exception text. The Actuator discovery page is disabled. Only read access to the health endpoint is enabled; environment, configuration, heap dumps, logger controls, metrics, mappings, migrations, and other diagnostic endpoints are unavailable. JMX endpoint exposure is disabled. Changing these defaults requires a review of authentication, network access, and sensitive data exposure.

Route probes through a private operator/orchestrator path where practical. The endpoints require no session token and reveal only coarse availability; restrict their public routing at the edge when external access is unnecessary. Configure probe timeouts and failure thresholds for the actual database/network latency. These checks are synchronous and depend on datasource connection timeouts; they do not provide an independent timeout or a distributed availability guarantee.

Example local check after startup:

```bash
curl --fail http://localhost:8080/actuator/health/liveness
curl --fail http://localhost:8080/actuator/health/readiness
```

The tools `/api/v1/tools/health` endpoint remains a compatibility/demo response and does not check the database. Use the readiness endpoint for traffic routing. No Compose or cloud healthcheck/restart policy is installed by this change; verify the chosen routing and orchestration settings during the full-stack rehearsal.

`HealthEndpointIntegrationTest` exercises healthy responses, datasource failure/recovery, liveness independence, and denial of diagnostic endpoints. The outage is injected into the datasource after application startup; a PostgreSQL network-outage rehearsal remains deployment evidence to collect. See [database migrations](./DATABASE_MIGRATIONS.md) and the [threat model](./THREAT_MODEL.md) for backup/restore and release checks.
