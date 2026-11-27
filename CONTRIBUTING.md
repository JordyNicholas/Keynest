# Contributing to Keynest

For using the product, start with the [Manager quickstart](README.md#quickstart-manager), [Java library example](examples/external-consumer/README.md) or [security model](docs/SECURITY_MODEL.md). This page is the development/engineering path for main; a release tag may predate the quality gates described here.

## Development setup

Use Java 21 and Maven 3.9+ for Java development; CI also tests Java 25 while compiling Java 21 bytecode. Use Node.js 22/npm for Angular. Docker with a usable daemon is required by the full Manager PostgreSQL tests. See [toolchain policy](docs/ADR-0005-java-and-toolchain-policy.md). Start the separate web app using the [README commands](README.md#run-manager-ui).

## Run API

For a disposable local API with in-memory H2:

```bash
mvn -pl apps/manager-api -am package -DskipTests
java -jar apps/manager-api/target/manager-api-0.1.1.jar
```

This quick development build skips tests; it is not a validation result. The default API listens on loopback port 8080, uses public demo key settings and loses H2 data when stopped. Use synthetic data. Run only one API runner on port 8080. For persistent local PostgreSQL use [Compose](docs/COMPOSE.md); for deployment use [the hosted guide](docs/DEPLOYMENT.md) with a non-demo key/credentials and the production profile.

The versioned JAR name follows the checked-out POM. Before upgrading an existing installation, use the [database/key backup and migration runbook](docs/DATABASE_MIGRATIONS.md). Never replace a retained vault key merely to make startup pass.

## Build and quality checks

```bash
mvn -B test
```

That runs Java tests, including Manager's real PostgreSQL checks, so Docker must be available. For only the libraries, follow the [independent example](examples/external-consumer/README.md). Web checks and build commands are in `apps/manager-web/package.json`; `npm run test:ci` needs a working headless Chrome.

Main's CI also enforces architecture, coverage, formatting and static analysis. Follow the documented commands and scopes rather than treating a plain test/reporting command as all CI gates:

| Gate | Source of truth |
| --- | --- |
| Two-space style and incremental formatting | [Coding standards](docs/CODING_STANDARDS.md), [Spotless check/apply and Git-history requirements](docs/design/security/JAVA_FORMATTING_GATES.md) |
| Dependency boundaries and real violation controls | [Architecture rules](docs/design/security/ARCHITECTURE_GATES.md) |
| Measured per-module coverage and missing-data rejection | [JaCoCo reporting/floors](docs/design/security/JAVA_COVERAGE_GATES.md) |
| Analyzer scope, source-bound reviewed cases and defect/repair controls | [Static analysis guide](docs/design/security/JAVA_STATIC_ANALYSIS.md) |

Formatter application can adopt a whole edited Java file. Any change to a source file with reviewed static-analysis cases invalidates its digest: re-review those cases before updating it. Use the guides' full checks; do not blindly refresh hashes or suppress a failing detector. Normal app/Docker/library builds do not activate these explicit analysis/formatting profiles.

## Manager API (auth and vault)

HTTP paths and their authentication decisions are maintained in [ApiPaths](apps/manager-api/src/main/java/dev/keynest/manager/api/ApiPaths.java) and the [central route catalog](apps/manager-api/src/main/java/dev/keynest/manager/api/RouteAccessCatalog.java), with inventory tests. Controllers define request/response binding. Read [the authentication boundary](docs/design/security/CENTRAL_AUTHENTICATION.md) before adding a route.

Use the [account overview](docs/ACCOUNT_OVERVIEW.md), [Vault/query guide](docs/VAULT_BROWSING.md), [export ADR](docs/ADR-0013-authenticated-vault-export.md) and [restore guide](docs/VAULT_RESTORE_WEB.md) for deeper contracts. HTTP password hash/verification tools process submitted data on the API; they are separate from the local-only homepage generator/checker. Development-only seal/open is disabled in the Docker profile. See [availability controls](docs/design/security/AVAILABILITY_CONTROLS.md) for selected route limits, shared Argon2 slots, full-table refusal and deployment limits; no universal status-code or distributed-budget guarantee is implied.

For configuration and production work use [deployment](docs/DEPLOYMENT.md), [health/operations](docs/OPERATIONS.md), [database recovery](docs/DATABASE_MIGRATIONS.md) and [sanitized security events](docs/SECURITY_EVENTS.md). Avoid logs/dumps containing passwords, tokens, provider credentials or vault contents.

## Engineering navigation

Read [AGENTS.md](AGENTS.md) before agent-assisted or repository engineering work. The [master roadmap](docs/MASTER_ROADMAP.md) is the execution authority; accepted ADRs and explicit maintainer decisions govern technical contracts. Use focused issues/PRs and preserve user data. Follow the repository's reviewed-change/merge policy; public setup is not authorization for deployment, provider calls or release publication.

Companions: [project charter](docs/PROJECT_CHARTER.md), [product](docs/PRODUCT.md), [historical engineering baseline](docs/ENGINEERING_BASELINE.md), [delivery plan](docs/DELIVERY_PLAN.md), [AI design boundaries](docs/AI_ARCHITECTURE.md), [project board](docs/reference/board.md) and [PR/project workflow](docs/PROJECT_PR_WORKFLOW.md). Baseline/release evidence is pinned to its recorded revision; use issues, reviewed PRs, current main and actual CI for later status. Planned designs are not shipped capabilities.
