# Changelog

## Unreleased — first MVP

### Libraries

- Embed password generation, policy validation, and encrypted envelopes through the standalone Java libraries. The [external consumer example](./examples/external-consumer/README.md) builds independently of Manager on Java 21 and 25.

### Manager

- Create, list, reveal, edit, and delete vault items. Manager Web automatically conceals revealed passwords. Vault cards show the time remaining to expiry and place secondary actions in a menu.
- Generate passwords in the browser with optional length, character-type, and keyword settings. The policy checker shows which rules the sample meets as you type.
- Rotate a saved password with the Vault policy or custom generation settings bounded by that policy. Show, hide, or copy a replacement before confirming the save.
- The optional BYOK workflow opens key setup in Account settings and displays the explanation on the built-in policy page. AI is disabled by default. See [BYOK policy explanations](./docs/AI_BACKEND.md) for configuration, data handling, and verification status.
- Local reminder rehearsals can simulate delivery without an email provider. External reminder email remains unavailable.

### Deployment and upgrades

- Packaged local setup uses PostgreSQL and a separately started Manager Web. Follow the [Compose guide](./docs/COMPOSE.md) for prerequisites and local configuration.
- Flyway manages schema changes. Back up the database and retain the matching vault key before upgrading. See [database migrations](./docs/DATABASE_MIGRATIONS.md) and [operations](./docs/OPERATIONS.md).
- Release verification and outstanding gates are tracked in the [MVP checklist](./docs/MVP_RELEASE_EVIDENCE.md).
