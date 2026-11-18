# Deploy Manager Web and Manager API

Manager Web works from a local browser or Vercel. Manager API and PostgreSQL run in Docker in each environment. Set the web build's public `KEYNEST_API_BASE_URL` for its target environment. Vercel Preview and Production use distinct HTTPS API origins, with a separate database volume, database password and vault key for each Compose project.

Run production on a dedicated Docker host. Prefer a second Docker host for test. If a test host is unavailable, [run the test stack locally](#local-test-fallback) and continue verification there. A hosted Preview walkthrough requires a reachable test API.

| Environment | Manager Web | Manager API | Compose project |
| --- | --- | --- | --- |
| Local development | `http://localhost:4200`, via `npm start` in `apps/manager-web` | `http://localhost:8080/api/v1` | `keynest-local`, local development Compose |
| Hosted test | Stable Vercel Preview alias, e.g. `https://keynest-test.example.com` | e.g. `https://keynest-api-test.example.com/api/v1` | `keynest-test`, hosted Compose |
| Production | Vercel Production domain, e.g. `https://keynest.example.com` | e.g. `https://keynest-api.example.com/api/v1` | `keynest-production`, hosted Compose |

A Vercel page using `localhost` or `127.0.0.1` contacts the machine running that browser. The browser can apply local-network permission rules even after a command-line CORS check succeeds. A hosted deployment needs an HTTPS address reachable by its users. Keep the existing local [development instructions](../README.md#run-api) for development on one machine.

## Verify and publish an image

CI builds and starts the Manager API with the hosted Compose definition and a disposable PostgreSQL instance. It verifies that the configured origin can send bearer and JSON requests, an unlisted origin is rejected, and the packaged API supports sign-in, Vault operations and tenant isolation.

For a deployment candidate on `main`, use GitHub **Actions → CI → Run workflow**, select `main`, and enable **Publish the verified Manager API image to GHCR**. Publication runs after the CI gates and pushes the exact Manager API image used by the hosted Compose smoke test. Use the digest in the run summary (`ghcr.io/<owner>/<repo>/manager-api@sha256:…`) in both environments. Give each Docker host registry pull access if the package is private. This workflow publishes a container image. The steps below control when each deployment changes.

## Deploy and verify hosted test

The Docker host needs HTTPS ingress with separate test and production hostnames. For each hostname, route `/api/v1/**` to the corresponding API loopback port, preserving the request path and query. Keep the Spring Actuator probe endpoints on loopback. Preserve the API's cache-control headers and bypass shared caching for `/api/v1/**`. Follow the [trusted proxy guidance](VERCEL.md#reverse-proxies-and-rate-limit-identity) before configuring any forwarded addresses.

Use a clean shell and an environment file stored privately on the Docker host. Compose gives exported shell values precedence over the file. Use a distinct, strong database password and random 32-byte hex vault key per environment, with a stable key ID. Keep the database password, vault key and volume on updates so existing data remains usable. For local files, names ending in `.local` are ignored by Git when the file name starts with `.env`.

| Required setting | Test | Production |
| --- | --- | --- |
| `KEYNEST_API_IMAGE` | Published digest from the verified CI run | Same digest verified in test |
| `KEYNEST_API_PORT` | e.g. `8081` | e.g. `8082` |
| `KEYNEST_CORS_ALLOWED_ORIGINS` | Exact preview web origin, no path or trailing slash | Exact production web origin, no path or trailing slash |
| `KEYNEST_DB_PASSWORD` | Test PostgreSQL password | Production PostgreSQL password |
| `KEYNEST_VAULT_KEY_HEX` | Test vault key | Production vault key |
| `KEYNEST_VAULT_KEY_ID` | Stable test key ID | Stable production key ID |

From the repository root on the Docker host, use the Compose project and environment file for test. PowerShell example with a private file under the current user's profile.

```powershell
$config = Join-Path $env:USERPROFILE 'keynest-deploy/.env.test.local'
docker compose --env-file "$config" -p keynest-test -f deploy/docker-compose.hosted.yml pull manager-api
if ($LASTEXITCODE -ne 0) { throw 'Manager API image pull failed' }
docker compose --env-file "$config" -p keynest-test -f deploy/docker-compose.hosted.yml up -d --wait
if ($LASTEXITCODE -ne 0) { throw 'Test stack startup failed' }
curl.exe --fail --silent --show-error --retry 20 --retry-delay 2 --retry-connrefused http://127.0.0.1:8081/actuator/health/readiness
if ($LASTEXITCODE -ne 0) { throw 'Test API is not ready' }
```

The readiness check retries while Spring starts. On Linux, supply the private env-file path directly and use `curl`.

1. In Vercel, set `KEYNEST_API_BASE_URL` for **Preview** (scope to the test branch if needed) to the test HTTPS URL including `/api/v1`. Redeploy the candidate commit. Use a [stable preview domain](https://vercel.com/docs/domains/working-with-domains/assign-domain-to-a-git-branch) in the test CORS allowlist and open that domain for testing.
2. In Edge DevTools, check that the request to `/api/v1/tools/health` reaches the test API and returns JSON. For a preflight on a Vault request, check that `Access-Control-Allow-Origin` matches the actual preview URL's origin. Verify sign-in and the Vault workflow with disposable test credentials. Keep real credentials and passwords out of test evidence.
3. To run a local web build against the same hosted test API, add the local web origin to the test allowlist (comma-separated), apply the Compose configuration, and [build Manager Web with the test API URL](VERCEL.md#manager-api-base-url-api_base_url). Serve the output with your local static server using a SPA fallback. The default `npm start` continues to use the localhost API.

## Local test fallback

Use Docker Desktop with Linux containers on Windows. Use the same hosted Compose file and test image digest. Set the local test env file's `KEYNEST_API_PORT` to `8080` and `KEYNEST_CORS_ALLOWED_ORIGINS` to `http://localhost:4200`. Use local test credentials and a new vault key. Port 8080 must be available. If the development stack is using it, stop that API from the repository root with `docker compose -p keynest-local -f deploy/docker-compose.yml stop manager-api`.

Run the test commands above using `-p keynest-test-local` and the local test env file, and probe port 8080 instead of 8081. From `apps/manager-web`, run `npm ci` and `npm start`, then open `http://localhost:4200`. Record this as local verification. Use a hosted test API for the Vercel browser check when it becomes available.

## Promote to production

1. Record the tested commit, image digest and browser results. After release approval, back up the existing production database and retain its vault key separately before applying a change. See the [restore rehearsal](DATABASE_MIGRATIONS.md#automated-backuprestore-rehearsal).
2. Use the same Compose commands with `-p keynest-production`, the private production env file, and its loopback port. Set the tested image digest there and confirm readiness through loopback and the production HTTPS API.
3. Configure the Vercel **Production** `KEYNEST_API_BASE_URL` with the production API URL. For controlled cutovers, [disable the automatic assignment of production domains](https://vercel.com/docs/deployments/promoting-a-deployment), stage a Production build of the same tested revision, then promote it after the API is healthy. Verify the production web health page and sign-in.
4. Keep the previous image digest and Vercel deployment. If rollback is needed, confirm database compatibility before switching the API image. Restore the retained-key backup only with an approved recovery plan. Vercel can roll its frontend back to the previous deployment independently.
