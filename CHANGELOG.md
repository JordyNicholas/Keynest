# Changelog

## Unreleased — first MVP

### Libraries

- Embed password generation, policy validation, and encrypted envelopes through the standalone Java libraries. The [external consumer example](./examples/external-consumer/README.md) builds independently of Manager on Java 21 and 25.

### Manager

- Create, list, reveal, update, and delete vault items. Revealed passwords are concealed automatically in Manager Web. The vault shows upcoming and overdue expiries and links to guided rotation.
- The optional BYOK workflow explains the built-in policy. AI is disabled by default. See [BYOK policy explanations](./docs/AI_BACKEND.md) for configuration, data handling, and verification status.
- Local reminder rehearsals can simulate delivery without an email provider. External reminder email remains unavailable.

### Deployment and upgrades

- Packaged local setup uses PostgreSQL and a separately started Manager Web. Follow the [Compose guide](./docs/COMPOSE.md) for prerequisites and local configuration.
- Flyway manages schema changes. Back up the database and retain the matching vault key before upgrading. See [database migrations](./docs/DATABASE_MIGRATIONS.md) and [operations](./docs/OPERATIONS.md).
- Release verification and outstanding gates are tracked in the [MVP checklist](./docs/MVP_RELEASE_EVIDENCE.md).
