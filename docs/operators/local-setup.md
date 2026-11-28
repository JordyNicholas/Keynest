# Run Keynest locally

For a PostgreSQL-backed trial, the supplied Compose file runs **Manager API and PostgreSQL 16**. Manager Web runs separately. This is a local demonstration topology with a public demo vault key, development database credentials and loopback-only host ports 8080/5432. It is not a production configuration.

## Prerequisites and first startup

- Docker Engine/Desktop with the Compose v2 plugin and network access to Maven Central/container registries.
- Free host ports 8080 (API) and 5432 (PostgreSQL).
- Node.js 22 and npm if you also run Manager Web; Java/Maven are supplied inside the API build stage.
- Python 3 if running the HTTP smoke test.

From the repository root:

```bash
docker compose -p keynest-local -f deploy/docker-compose.yml up --build -d
curl --fail http://localhost:8080/actuator/health/readiness
```

The first build downloads Maven dependencies and images. PostgreSQL must pass `pg_isready` before the API starts. Flyway migrates the fresh database and Hibernate validates it. Wait until readiness returns `{"status":"UP"}`; a refused connection during startup is not readiness. If startup fails, inspect container status and local logs privately. Never paste vault keys, tokens, request bodies or database dumps into an issue.

The database lives in the project-scoped `keynest_pg` volume. For an existing installation, follow the [migration and backup runbook](database-migrations.md) before rebuilding against a new revision. Keep the exact vault key with any database you expect to open later.

## Run the web application

```bash
cd apps/manager-web
npm ci
npm start
```

Open `http://localhost:4200`. The development web build targets `http://localhost:8080/api/v1`; the Compose API allows that web origin. See the [Manager user guide](../manager/README.md) for the next steps.

For a separately hosted static frontend, including Vercel, build with its reachable HTTPS API destination using `KEYNEST_API_BASE_URL` and configure the deployed API's exact CORS origins. The local demo Compose must first be adapted to real key/database custody and TLS before exposing it. See [web deployment configuration](web-deployment.md) and [health probe routing](runtime.md). No hosted-provider deployment is claimed by this local rehearsal.

## Run API with H2

For a local in-memory API, use JDK 21 and Maven 3.9 or newer. Stop the Compose API first if it occupies port 8080. From the repository root:

```sh
mvn -pl apps/manager-api -am package -DskipTests
java -jar apps/manager-api/target/manager-api-0.1.1.jar
```

The standalone profile listens on `127.0.0.1:8080` and uses H2 in memory. Accounts and Vault items are lost when the process exits. Start the web application using the same commands as above.

## Disposable HTTP smoke test

Use a fresh, isolated project with no existing data and ports 8080/5432 free. On unreleased `main`, signup is paused; this test explicitly seeds synthetic existing-owner rows in PostgreSQL. It is not a production onboarding procedure:

```bash
export COMPOSE_PROJECT_NAME=keynest-ci-0-1
docker compose -f deploy/docker-compose.yml up --build -d
python3 scripts/compose-smoke.py --disposable
docker compose -f deploy/docker-compose.yml down --volumes --remove-orphans
```

The final command permanently removes **only that project's test containers and database volume**. Do not run it against an installation whose data you want to keep. Stop your normal local project first if it owns these ports; stopping with `down` without `--volumes` retains its database.

The smoke test waits up to 120 seconds for readiness, then verifies liveness, restricted diagnostics, disabled demo seal, retired-registration refusal and existing-owner login/logout, generated vault create/list/reveal/update/delete, reveal cache headers, POST-only reveal and two-user access denial. It uses constrained `example.invalid` database fixtures with a known test-only credential and prints only pass/fail outcomes. The seeding guard rejects ordinary deployment project names; see the [activation transition](account-activation.md). HTTP redirects and proxy environment variables are ignored so test bearers stay on the loopback API. The script creates two test accounts; their removal relies on deletion of the disposable volume.

CI's `Packaged Compose smoke` job performs the same image build and HTTP test after Java and Angular gates pass, with a run-specific project name and unconditional project cleanup. Dumps, keys, response bodies and container logs are not uploaded as artifacts. CI also runs [authenticated browser fixtures](../security/controls/web-csp-verification.md) against the packaged API. These are disposable local checks. Refer to the workflow result for the revision being tested. Verify your own provider, hosting, and TLS ingress separately.

## Stop, restart and remaining deployment work

```bash
docker compose -p keynest-local -f deploy/docker-compose.yml down
docker compose -p keynest-local -f deploy/docker-compose.yml up -d
```

These commands retain the local database volume. Never remove that volume as an upgrade or recovery shortcut. Restarting with a different vault key makes existing envelopes unreadable.

For separate test and production environments, use the [hosted Compose workflow](deployment.md) with an HTTPS ingress and distinct state and keys. Rehearse backups with separately retained key material. The local sample has no API healthcheck or restart policy. Manager Web runs separately.

## API runtime controls

The API image runs as UID/GID 10001. Compose sets a read-only root filesystem, drops all Linux capabilities, forbids privilege escalation, and limits the API to 1 GiB memory, two CPUs, 256 processes/threads and zero core-dump size. Its 64 MiB `/tmp` tmpfs is writable and has `nosuid,nodev`; it intentionally permits executable mappings because native Argon2/JNA extracts a shared library there. This exception is verified by the packaged registration/login smoke test.

The image's `JAVA_TOOL_OPTIONS` disables attach-based diagnostics, automatic OOM heap dumps, native crash core dumps and fatal-error files. This reduces diagnostic leakage but also removes crash investigation artifacts and tools such as `jcmd` attach. Operators must deliberately review any override; these settings do not protect against a compromised host/process or privileged memory capture. Resource limits are a baseline for the local sample, not capacity planning for real concurrency. Argon2 memory is outside ordinary Java heap budgeting.

Both published ports bind only to `127.0.0.1`. Existing remote demo clients must use an explicitly secured proxy/forwarding setup rather than relying on all-interface exposure. Rollback restores the prior image/Compose file without changing the database, but reintroduces root execution and the earlier exposure/diagnostic defaults. Keep the same key and database during either direction of an image change.

CI runs `scripts/check-container-runtime.py` against the actual container before HTTP smoke tests. It checks the user, Docker controls and JVM flags without dumping environment/configuration. These controls apply to Manager API; PostgreSQL retains its image's own user/runtime model.
