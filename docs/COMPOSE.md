# Local Compose runbook

The supplied Compose file runs **Manager API and PostgreSQL 16**. Manager Web runs separately. This is a local demonstration topology with a public demo vault key, development database credentials and host ports 8080/5432. It is not a production configuration.

## Prerequisites and first startup

- Docker Engine/Desktop with the Compose v2 plugin and network access to Maven Central/container registries.
- Free host ports 8080 (API) and 5432 (PostgreSQL).
- Node/npm matching the Angular toolchain if you also run Manager Web; Java/Maven are supplied inside the API build stage.
- Python 3 if running the HTTP smoke test.

From the repository root:

```bash
docker compose -p keynest-local -f deploy/docker-compose.yml up --build -d
curl --fail http://localhost:8080/actuator/health/readiness
```

The first build downloads Maven dependencies and images. PostgreSQL must pass `pg_isready` before the API starts. Flyway migrates the fresh database and Hibernate validates it. Wait until readiness returns `{"status":"UP"}`; a refused connection during startup is not readiness. If startup fails, inspect container status and local logs privately. Never paste vault keys, tokens, request bodies or database dumps into an issue.

The database lives in the project-scoped `keynest_pg` volume. For an existing installation, follow the [migration and backup runbook](./DATABASE_MIGRATIONS.md) before rebuilding against a new revision. Keep the exact vault key with any database you expect to open later.

## Run the web application

```bash
cd apps/manager-web
npm ci
npm start
```

Open `http://localhost:4200`. The development web build targets `http://localhost:8080/api/v1`; the Compose API allows that web origin. Register, create an item, reveal it, and delete it. The full Material vault UX remains separate delivery work.

For a separately hosted static frontend, including Vercel, build with its reachable HTTPS API destination using `KEYNEST_API_BASE_URL` and configure the deployed API's exact CORS origins. The local demo Compose must first be adapted to real key/database custody and TLS before exposing it. See [web deployment configuration](./VERCEL.md) and [health probe routing](./OPERATIONS.md). No hosted-provider deployment is claimed by this local rehearsal.

## Disposable HTTP smoke test

Use a fresh, isolated project with no existing data and ports 8080/5432 free:

```bash
docker compose -p keynest-rehearsal -f deploy/docker-compose.yml up --build -d
python3 scripts/compose-smoke.py --disposable
docker compose -p keynest-rehearsal -f deploy/docker-compose.yml down --volumes --remove-orphans
```

The final command permanently removes **only that project's test containers and database volume**. Do not run it against an installation whose data you want to keep. Stop your normal local project first if it owns these ports; stopping with `down` without `--volumes` retains its database.

The smoke test waits up to 120 seconds for readiness, then verifies liveness, restricted diagnostics, disabled demo seal, registration/login/logout, generated vault create/list/reveal/update/delete, reveal cache headers, POST-only reveal and two-user access denial. It generates disposable account credentials in memory and prints only pass/fail outcomes. HTTP redirects and proxy environment variables are ignored so test bearers stay on the loopback API. The script creates two test accounts; their removal relies on deletion of the disposable volume.

CI's `Packaged Compose smoke` job performs the same image build and HTTP test after Java and Angular gates pass, with a run-specific project name and unconditional project cleanup. Dumps, keys, response bodies and container logs are not uploaded as artifacts. A passing job demonstrates the packaged API/PostgreSQL path on a fresh runner; it is not a browser UI or real-provider/cloud/TLS rehearsal.

## Stop, restart and remaining deployment work

```bash
docker compose -p keynest-local -f deploy/docker-compose.yml down
docker compose -p keynest-local -f deploy/docker-compose.yml up -d
```

These commands retain the local database volume. Never remove that volume as an upgrade or recovery shortcut. Restarting with a different vault key makes existing envelopes unreadable.

Before public deployment: replace demo/database credentials, activate `docker,prod`, keep the demo-key opt-in off, restrict database exposure, install TLS/proxy and exact CORS configuration, tune limits/timeouts, and rehearse backups with separately retained key material. The current image has no non-root user/heap-dump/container-resource hardening and Compose has no API healthcheck/restart policy. The web application is not included in Compose. Track these limitations in the release review rather than assuming the local sample provides them.
