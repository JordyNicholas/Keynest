# Keynest

Password tools you can embed, and a self-hostable password vault built on the same Java libraries.

Use Manager to generate and save passwords, review expiry, rotate credentials and restore passphrase-protected `.keynest` backups. Use the libraries independently when your application needs generation, policy validation or encryption.

**Published source release:** [v0.1.1](https://github.com/JordyNicholas/Keynest/releases/tag/v0.1.1) · [release setup and limitations](docs/releases/0.1.1.md) · [Apache-2.0](LICENSE).

This README on **main** describes the development checkout, which includes changes after v0.1.1. For the published release's behavior and setup, open the tagged source and its release guide. Repository version numbers alone do not mean later main changes have been released.

## Product definition

### Two offerings

| I want to… | Start here |
| --- | --- |
| Try Manager on my own machine | [Local quickstart below](#quickstart-manager) |
| Use the Java libraries without the app | [Standalone library example](examples/external-consumer/README.md) |
| Understand who can access my data | [Security model and limits](docs/SECURITY_MODEL.md) |
| Deploy beyond my own machine | [Deployment guide](docs/DEPLOYMENT.md), after reading the security model |

### Vision

One policy-driven workflow for generation, validation and password management, with reusable libraries available separately from Manager.

### Problems we solve

Manager provides a searchable Vault, password generation/checking, rotation and expiry management, plus encrypted native backup/restore with explicit duplicate decisions. The homepage's Generate and Check panels work without an account using fixed default rules; [their values stay local](docs/SECURITY_MODEL.md#public-tools-and-manager-data).

### Library distribution

Build the three libraries from source into your local Maven repository, then run the [independent consumer](examples/external-consumer/README.md). It verifies generation/validation, seal/open, rewrapping and wrong-key/context rejection while banning Manager/Spring dependencies. No API, database, Node or AI service is needed for that path. Registry publication remains future work.

## Prerequisites

| Path | Install |
| --- | --- |
| Local Manager quickstart | Docker Engine/Desktop with Compose v2; Node.js 22 and npm; free ports 8080, 5432 and 4200 |
| Java library example | Java 21+ and Maven 3.9+ |
| Development and full tests | See [contributor setup](CONTRIBUTING.md); Manager's PostgreSQL tests also need Docker |

The API's Docker build supplies Java 21/Maven. Initial builds need access to container registries, Maven Central and npm. CI tests Java 21/25 with Java 21 bytecode; that is not a claim of testing every OS/browser.

## Quickstart Manager

For a **new local demonstration with synthetic data**. The sample database credentials and vault key are public demo values. For an existing installation, follow the [backup/upgrade runbook](docs/DATABASE_MIGRATIONS.md) first; for real deployment use separately configured credentials/key and the [deployment guide](docs/DEPLOYMENT.md).

Start from the repository root of your chosen checkout. For v0.1.1, use the [release guide's tag/archive instructions](docs/releases/0.1.1.md#get-the-source); for unreleased development, use main.

### Docker (API)

```bash
docker compose -p keynest-local -f deploy/docker-compose.yml up --build -d
curl --fail http://localhost:8080/actuator/health/readiness
```

Wait for HTTP 200 with `{"status":"UP"}`; startup can take time, so repeat the readiness check if the API is still starting. In PowerShell, use `curl.exe` for this command. Compose runs **API + PostgreSQL**; the web app starts separately. See [startup/troubleshooting](docs/COMPOSE.md).

### Run Manager UI

In a second terminal, from the same repository root:

```bash
cd apps/manager-web
npm ci
npm start
```

Open [http://localhost:4200](http://localhost:4200). Use Generate/Check on the homepage without signing in, or use the header's sign-in dialog to create an account and use your Vault. Keep this demo's entries synthetic. `http://127.0.0.1:4200` is a different browser origin; its API requests are not allowed by default CORS.

Stop the API/database from the repository root while keeping their volume:

```bash
docker compose -p keynest-local -f deploy/docker-compose.yml down
```

Do not add `--volumes` for data you want to keep. Preserve the database's matching vault key as well. The [Compose runbook](docs/COMPOSE.md) covers restart and isolated smoke tests.

### Security stance

Manager decrypts passwords **on the server** using a server-wide key. Trust the host/operator and browser. Vault metadata is plaintext in PostgreSQL, and browser JavaScript can read the session bearer. Read the short [security model](docs/SECURITY_MODEL.md) before using real credentials; it links backup/recovery and deployment requirements.

### Current release limitations

AI explanation is optional, disabled by default and needs explicit provider configuration; live-provider verification remains pending. Shared rate limiting, external reminder delivery, MFA, verified-email activation, OIDC/SSO and agent capabilities are not enabled by the public setup above. See [current trust boundaries and deferred features](docs/SECURITY_MODEL.md#current-limits) and the published version's [release limitations](docs/releases/0.1.1.md#limitations-and-deferred-scope).

## Build & test

Developer setup, full test prerequisites and quality-gate commands are in [CONTRIBUTING.md](CONTRIBUTING.md). The library example has its own smaller [build/run path](examples/external-consumer/README.md#build-and-run).

### Run API

For an alternative standalone API using disposable in-memory H2, see [local API development](CONTRIBUTING.md#run-api). The Compose quickstart above is the local PostgreSQL path.

### Vercel (Manager Web)

Use the Angular project under `apps/manager-web` and the [web deployment guide](docs/VERCEL.md). Configure a reachable API destination and exact CORS origins for that environment. The local demo and development server are not a public hosting setup.

## Manager API (auth and vault)

For HTTP integration, start with [API development references](CONTRIBUTING.md#manager-api-auth-and-vault), [Vault metadata/query contracts](docs/VAULT_BROWSING.md) and [native restore](docs/VAULT_RESTORE_WEB.md). For reusable Java APIs, use the standalone example instead.

### Password policy schemas (Zod-like contract)

Policies are JSON documents checked against a JSON Schema. See the [policy schema](docs/schemas/password-policy.schema.json), [default document](docs/policies/keynest.default.v1.json) and [schema/loader contract](docs/ADR-0004-password-policy-schema.md).

### Target stack

Java 21, Spring Boot 4, Angular 21/Material, PostgreSQL 16 and Docker Compose. See [toolchain policy](docs/ADR-0005-java-and-toolchain-policy.md).

## Repo layout

- `libs/`: independent policy, generation and Seal libraries.
- `apps/manager-api` and `apps/manager-web`: Manager backend and web app.
- `deploy/`: local/hosted Compose and container configuration.
- `docs/`: public guides, operations, contracts and engineering references.

## MVP status

The [release evidence](docs/MVP_RELEASE_EVIDENCE.md) attributes tests to their actual revisions/environments. Main's subsequent automated checks do not turn historical manual results into verification of a newer release. See [changelog](CHANGELOG.md) and [release notes](docs/releases/0.1.1.md).

## Contributing and engineering

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, coding/quality gates and engineering navigation. The [canonical roadmap](docs/MASTER_ROADMAP.md) governs development scope; it is separate from the public setup path. Report vulnerabilities through the private process in [SECURITY.md](SECURITY.md), keeping real credentials/keys/data out of public issues.

## Rename

The repository is [JordyNicholas/Keynest](https://github.com/JordyNicholas/Keynest).
