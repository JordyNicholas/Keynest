# Keynest

Keynest is a self-hosted password vault and a set of Java libraries for generating, validating, and encrypting passwords. The [Manager guide](docs/manager/README.md) covers generating and saving passwords, expiry, rotation, and encrypted backup and restore.

| I want to… | Start here |
| --- | --- |
| Run my own vault | [Manager quickstart](#quickstart-manager) |
| Embed the Java tools in an application | [Java library guide](docs/libraries/README.md) |
| Understand who can access my data | [Security model and limits](docs/security/overview.md) |
| Find a guide or reference | [Documentation](docs/README.md) |

The latest published package is [v0.1.1](https://github.com/JordyNicholas/Keynest/releases/tag/v0.1.1), distributed as source under [Apache-2.0](LICENSE). Use the [tagged checkout or source archive](docs/releases/0.1.1.md#get-the-source) to run that version. The documentation on `main` also covers [unreleased changes](CHANGELOG.md#unreleased).

## Prerequisites

- Manager quickstart needs Docker with Compose v2, Node.js 22 with npm, and free ports 8080, 5432, and 4200. Docker supplies the API's Java and Maven toolchain.
- The [Java library example](examples/external-consumer/README.md) needs JDK 21 and Maven 3.9 or newer.
- The [contributor guide](CONTRIBUTING.md) covers the full development and test prerequisites.

<a name="quickstart"></a>

## Quickstart Manager

Use synthetic data for this local trial. The Compose project uses a public demo key and development database credentials. For an existing database, first read the [backup and migration runbook](docs/operators/database-migrations.md). For a persistent or shared installation, follow the [deployment guide](docs/operators/deployment.md).

### Docker (API)

From the repository root:

```sh
docker compose -p keynest-local -f deploy/docker-compose.yml up --build -d
curl --fail http://localhost:8080/actuator/health/readiness
```

Wait for `{"status":"UP"}` before continuing. Repeat the readiness check while the API starts. In PowerShell, use `curl.exe`. Compose starts Manager API and PostgreSQL. The [local setup guide](docs/operators/local-setup.md) covers startup and troubleshooting.

### Run Manager UI

In a separate terminal, from the same checkout:

```sh
cd apps/manager-web
npm ci
npm start
```

Open [http://localhost:4200](http://localhost:4200). Use **Generate a password** or **Check a password** without an account, or sign in as an existing owner and choose **Vault → Add Vault item**. On unreleased `main`, new signup is paused during the [verified-email transition](docs/operators/account-activation.md); fresh instances cannot onboard owners yet. The default API allows the `http://localhost:4200` browser origin.

To stop API and PostgreSQL while retaining the database volume, run this from the repository root:

```sh
docker compose -p keynest-local -f deploy/docker-compose.yml down
```

## Security and recovery

Manager opens saved passwords on the API server with a server-wide key. Vault labels, website URLs, and usernames are plaintext database metadata. The browser keeps a tab-scoped bearer accessible to JavaScript. Keep the matching vault key separately from database backups. A passphrase-protected `.keynest` export restores selected items. For accounts and full-instance recovery, use the [database backup and recovery runbook](docs/operators/database-migrations.md).

Password tools and the Vault work with AI disabled. Optional policy explanations use your own provider credentials. Live-provider verification is pending. Read the [security model](docs/security/overview.md) before using real credentials, and see the [security policy](SECURITY.md) for reporting a vulnerability.

## Developer and integration entry points

### Build & test

See [contributor setup](CONTRIBUTING.md) for Java and Angular tests, Docker prerequisites, quality gates, and pull requests.

### Run API

Use the [standalone API instructions](docs/operators/local-setup.md#run-api-with-h2) for a loopback development API with in-memory H2.

### Vercel (Manager Web)

See [static Web deployment](docs/operators/web-deployment.md) for both supported Vercel roots, same-origin and split-origin builds, and response headers.

## Manager API (auth and vault)

Browse the [HTTP API reference](docs/api/README.md) for sessions, Vault, account policy, and password tools.
