# Keynest

Keynest is a self-hosted password vault and a set of Java libraries for generating, validating, and encrypting passwords.

| I want to… | Start here |
| --- | --- |
| Run my own vault | [Manager quickstart](#quickstart) |
| Embed the Java tools in an application | [Java library guide](docs/libraries/README.md) |
| Find a guide or reference | [Documentation](docs/README.md) |

The latest published package is [v0.1.1](https://github.com/JordyNicholas/Keynest/releases/tag/v0.1.1), distributed as source. Use its tag for release-specific setup and limitations. The documentation on `main` also covers [unreleased changes](CHANGELOG.md#unreleased).

## Quickstart

For a local trial, you'll need Docker with Compose v2 and Node.js 22 with npm. From the repository root, start Manager API and PostgreSQL:

```sh
docker compose -p keynest-local -f deploy/docker-compose.yml up --build -d
curl --fail http://localhost:8080/actuator/health/readiness
```

When readiness returns `{"status":"UP"}`, start Manager Web in a separate terminal:

```sh
cd apps/manager-web
npm ci
npm start
```

Open [http://localhost:4200](http://localhost:4200). Create an account, then open **Vault → Add Vault item** to save your first login. The local Compose project uses a public demo key and development database credentials. For persistent or shared deployments, follow the [deployment guide](docs/operators/deployment.md). The [local setup guide](docs/operators/local-setup.md) covers prerequisites, shutdown, and troubleshooting.

## Security and recovery

Manager opens vault passwords on the API server with a server-wide key. Vault labels, website URLs, and usernames are plaintext database metadata. The browser keeps a tab-scoped session bearer accessible to JavaScript. Keep the matching vault key separately from database backups so the database can be recovered. A passphrase-protected `.keynest` export restores selected Vault items. For full-instance recovery, use the [database backup and recovery runbook](docs/operators/database-migrations.md).

Password tools and the Vault work with AI disabled. Optional policy explanations use your own provider credentials and send the built-in policy to the configured provider. Live-provider verification is pending. The [threat model](docs/security/threat-model.md) describes the trust boundaries, per-instance limits, and evidence. Report suspected vulnerabilities using the [security policy](SECURITY.md).

## Developer and integration entry points

### Build & test

Run `mvn test` from the repository root with JDK 21 and Maven 3.9 or newer. See the [contributor guide](docs/contributing/README.md) for Angular tests, build gates, and pull requests.

### Run API

Use the [standalone API instructions](docs/operators/local-setup.md#run-api-with-h2) for a loopback development API with in-memory H2.

### Run Manager UI

Follow [local web startup](docs/operators/local-setup.md#run-the-web-application) or the [Manager Web development guide](apps/manager-web/README.md).

### Docker (API)

See [local Compose setup](docs/operators/local-setup.md) for a disposable demo or [deployment](docs/operators/deployment.md) for an isolated test or production stack.

### Vercel (Manager Web)

See [static Web deployment](docs/operators/web-deployment.md) for both supported Vercel roots, same-origin and split-origin builds, and response headers.

## Manager API (auth and vault)

Browse the [HTTP API reference](docs/api/README.md) for sessions, Vault, account policy, and password tools.
