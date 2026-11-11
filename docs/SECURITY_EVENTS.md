# HTTP security events

Manager emits a fixed-field `security_event` line through logger `dev.keynest.manager.security.SecurityEventLogger`. See accepted [ADR-0009](./ADR-0009-sanitized-http-security-events.md) for the scope and limits.

| Field | Source |
| --- | --- |
| `schema_version` | Constant `1` |
| `event_id` | Server-generated UUID; never a client correlation ID |
| `occurred_at` | Server-generated UTC instant |
| `action` | Fixed enum: AUTH_REGISTER/LOGIN/LOGOUT/ME or VAULT_LIST/READ/CREATE/UPDATE/DELETE/REVEAL |
| `outcome` | SUCCESS (2xx), REJECTED (4xx), ERROR (5xx/escaping exception), OTHER |
| `http_status` | Observed response status, or synthetic 500 for an escaping exception |

No request or response payload, path, query, header, exception, token, user/resource identifier, IP or vault metadata is read into the log entry. IDs in item paths are used only to match a route shape; they are not emitted. Unsupported methods, unknown/malformed item paths, public tools/health and asynchronous requests do not emit events.

These are synchronous HTTP observations. They are not durable audit records, transaction-commit evidence, authenticated actor attribution or a complete attack history. An escaping exception may be handled later with a different final response. Runtime logging failures do not change the request outcome; event delivery is best-effort. Async event support and durable/attributed auditing remain future work.

Operators must restrict log access, bound rotation/retention, and review collector destinations. Keep body/header capture and secret-bearing diagnostic logging disabled in proxy/framework/APM layers. Do not add user data to MDC or logger patterns or route this stream to models by default. Retention and back-pressure are controlled by the chosen logging backend; this change installs no sink or retention service.

To suppress this logger explicitly:

```properties
logging.level.dev.keynest.manager.security.SecurityEventLogger=OFF
```

The privacy and residual-risk rules in the [threat model](./THREAT_MODEL.md) still apply. Tests assert actual captured log contents with synthetic secret sentinels, error handling and rate-limit outcomes; release verification must repeat these checks after future route/logging changes.
