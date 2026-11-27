# Contributing to Keynest

For using the product, start with the [Manager quickstart](README.md#quickstart-manager), [Java library example](examples/external-consumer/README.md) or [security model](docs/security/overview.md). Use the [contributor documentation index](docs/contributing/README.md) for repository layout, project planning, and the pull request workflow. This page describes development on `main`.

## Development setup

Use Java 21 and Maven 3.9+ for Java development; CI also tests Java 25 while compiling Java 21 bytecode. Use Node.js 22/npm for Angular. Docker with a usable daemon is required by the full Manager PostgreSQL tests. See [toolchain policy](docs/architecture/decisions/ADR-0005-java-and-toolchain-policy.md). Start the separate web app using the [README commands](README.md#run-manager-ui).

## Run API

Follow the [standalone H2 instructions](docs/operators/local-setup.md#run-api-with-h2) for a disposable loopback API. For PostgreSQL-backed development and hosted deployments, see [operations and deployment](docs/operators/README.md).

## Build and quality checks

```bash
mvn -B test
```

That runs Java tests, including Manager's real PostgreSQL checks, so Docker must be available. For only the libraries, follow the [independent example](examples/external-consumer/README.md). Web checks and build commands are in `apps/manager-web/package.json`; `npm run test:ci` needs a working headless Chrome.

Main's CI also enforces architecture, coverage, formatting and static analysis. Follow the documented commands and scopes rather than treating a plain test/reporting command as all CI gates:

| Gate | Source of truth |
| --- | --- |
| Two-space style and incremental formatting | [Coding standards](docs/contributing/coding-standards.md), [Spotless check/apply and Git-history requirements](docs/contributing/quality/formatting.md) |
| Dependency boundaries and real violation controls | [Architecture rules](docs/contributing/quality/architecture.md) |
| Measured per-module coverage and missing-data rejection | [JaCoCo reporting/floors](docs/contributing/quality/coverage.md) |
| Analyzer scope, source-bound reviewed cases and defect/repair controls | [Static analysis guide](docs/contributing/quality/static-analysis.md) |

Formatter application can adopt a whole edited Java file. Any change to a source file with reviewed static-analysis cases invalidates its digest: re-review those cases before updating it. Use the guides' full checks; do not blindly refresh hashes or suppress a failing detector. Normal app/Docker/library builds do not activate these explicit analysis/formatting profiles.

## Manager API (auth and vault)

HTTP paths and their authentication decisions are maintained in [ApiPaths](apps/manager-api/src/main/java/dev/keynest/manager/api/ApiPaths.java) and the [central route catalog](apps/manager-api/src/main/java/dev/keynest/manager/api/RouteAccessCatalog.java), with inventory tests. Controllers define request/response binding. Read [the authentication boundary](docs/security/controls/api-authentication.md) before adding a route.

Use the [account overview](docs/manager/account.md), [Vault/query guide](docs/api/vault-browsing.md), [export ADR](docs/architecture/decisions/ADR-0013-authenticated-vault-export.md) and [restore guide](docs/manager/backup-and-restore.md) for deeper contracts. HTTP password hash/verification tools process submitted data on the API; they are separate from the local-only homepage generator/checker. Development-only seal/open is disabled in the Docker profile. See [availability controls](docs/security/controls/availability.md) for selected route limits, shared Argon2 slots, full-table refusal and deployment limits; no universal status-code or distributed-budget guarantee is implied.

For configuration and production work use [deployment](docs/operators/deployment.md), [health/operations](docs/operators/runtime.md), [database recovery](docs/operators/database-migrations.md) and [sanitized security events](docs/security/events.md). Avoid logs/dumps containing passwords, tokens, provider credentials or vault contents.

## Engineering navigation

Read [AGENTS.md](AGENTS.md) before agent-assisted or repository engineering work. The [master roadmap](docs/contributing/project/roadmap.md) is the execution authority; accepted ADRs and explicit maintainer decisions govern technical contracts. Use focused issues/PRs and preserve user data. Follow the repository's reviewed-change/merge policy; public setup is not authorization for deployment, provider calls or release publication.

Companions: [project charter](docs/contributing/project/charter.md), [product](docs/contributing/project/product.md), [historical engineering baseline](docs/archive/engineering-baseline.md), [delivery plan](docs/archive/delivery-plan.md), [AI design boundaries](docs/design/ai-and-agents.md), [project board](docs/contributing/project/board.md) and [PR/project workflow](docs/contributing/pull-requests.md). Baseline/release evidence is pinned to its recorded revision; use issues, reviewed PRs, current main and actual CI for later status. Planned designs are not shipped capabilities.
