# Deploy Manager Web and Manager API

Sign-in uses independent client/route and normalized-email admission limits. Both Compose profiles forward the four `KEYNEST_LOGIN_THROTTLE_*` settings; see [defaults, cooldown/recovery, bounded capacity and restart/replica limits](../security/controls/login-throttling.md). These are process-local controls and do not replace shared ingress protection or database/request deadlines.

Manager Web works from a local browser or Vercel. Manager API and PostgreSQL run in Docker in each environment. Set the web build's public `KEYNEST_API_BASE_URL` for its target environment. Vercel Preview and Production use distinct HTTPS API origins, with a separate database volume, database password and vault key for each Compose project.

Run production on a dedicated Docker host. Prefer a second Docker host for test. If a test host is unavailable, [run the test stack locally](#local-test-fallback). To include Vercel Preview in that test, [create a temporary HTTPS URL for the local API](#test-vercel-preview-with-a-local-api).

| Environment | Manager Web | Manager API | Compose project |
| --- | --- | --- | --- |
| Local development | `http://localhost:4200`, via `npm start` in `apps/manager-web` | `http://localhost:8080/api/v1` | `keynest-local`, local development Compose |
| Hosted test | Stable Vercel Preview alias, e.g. `https://keynest-test.example.com` | e.g. `https://keynest-api-test.example.com/api/v1` | `keynest-test`, hosted Compose |
| Production | Vercel Production domain, e.g. `https://keynest.example.com` | e.g. `https://keynest-api.example.com/api/v1` | `keynest-production`, hosted Compose |

A Vercel page using `localhost` or `127.0.0.1` contacts the machine running that browser. The browser can apply local-network permission rules even after a command-line CORS check succeeds. A hosted deployment needs an HTTPS address reachable by its users. Keep the existing local [development instructions](../../README.md#run-api) for development on one machine.

For local development, choose one API runner. `npm run start-api` starts the standalone Java API, and `npm run start-container` starts the local Compose API with PostgreSQL. Both use port 8080 by default. Use the hosted Compose commands below for production-profile startup.

Before public exposure, review [availability controls and saturation recovery](../security/controls/availability.md): trusted direct proxies, body/read/request deadlines and shared edge admission remain deployment responsibilities. Local 429/503 protections do not reserve capacity per owner or provide a distributed budget.

## Verify and publish an image

CI builds and starts the Manager API with the hosted Compose definition and a disposable PostgreSQL instance. It verifies that the configured origin can send bearer and JSON requests, an unlisted origin is rejected, and the packaged API supports sign-in, Vault operations and tenant isolation.

For a deployment candidate on `main`, use GitHub **Actions → CI → Run workflow**, select `main`, and enable **Publish the verified Manager API image to GHCR**. Publication runs after the CI gates and pushes the exact Manager API image used by the hosted Compose smoke test. Use the digest in the run summary (`ghcr.io/<owner>/<repo>/manager-api@sha256:…`) in both environments. Give each Docker host registry pull access if the package is private. This workflow publishes a container image. The steps below control when each deployment changes.

## Deploy and verify hosted test

The Docker host needs HTTPS ingress with separate test and production hostnames. For each hostname, route `/api/v1/**` to the corresponding API loopback port, preserving the request path and query. Keep the Spring Actuator probe endpoints on loopback. Preserve the API's cache-control headers and bypass shared caching for `/api/v1/**`. Follow the [trusted proxy guidance](web-deployment.md#reverse-proxies-and-rate-limit-identity) before configuring any forwarded addresses.

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
3. To run a local web build against the same hosted test API, add the local web origin to the test allowlist (comma-separated), apply the Compose configuration, and [build Manager Web with the test API URL](web-deployment.md#manager-api-base-url-api_base_url). Serve the output with your local static server using a SPA fallback. The default `npm start` continues to use the localhost API.

## Local test fallback

Use Docker Desktop with Linux containers on Windows. Use the same hosted Compose file and test image digest. Set the local test env file's `KEYNEST_API_PORT` to `8080` and `KEYNEST_CORS_ALLOWED_ORIGINS` to `http://localhost:4200`. Use local test credentials and a new vault key. Port 8080 must be available. If the development stack is using it, stop that API from the repository root with `docker compose -p keynest-local -f deploy/docker-compose.yml stop manager-api`.

Run the test commands above using `-p keynest-test-local` and the local test env file, and probe port 8080 instead of 8081. From `apps/manager-web`, run `npm ci` and `npm start`, then open `http://localhost:4200`. Record this as local verification.

## Test Vercel Preview with a local API

Use this PowerShell walkthrough when you need to test a Vercel Preview and have only Docker Desktop available. The tunnel URL is public while the tunnel runs. Use a fresh `keynest-test-local` stack with disposable accounts, vault entries and credentials. Keep the test on a dedicated Preview branch and use its stable web origin throughout.

1. Follow the [local test fallback](#local-test-fallback). In its private env file, set `KEYNEST_CORS_ALLOWED_ORIGINS` to your exact Preview web origin, such as `https://keynest-test.example.com`. You can include `http://localhost:4200` as a comma-separated second origin if you are also running the local frontend. Apply the env file with the same `docker compose … up -d --wait` command and check the API readiness endpoint on port 8080.
2. Start a temporary Caddy proxy on the test Compose network. It forwards `/api/v1` requests, keeps Actuator behind loopback, and marks API responses `no-store`.

   ```powershell
   $caddyfile = (New-TemporaryFile).FullName
   @'
   :8080 {
     @api path /api/v1 /api/v1/*
     handle @api {
       reverse_proxy manager-api:8080 {
         header_down Cache-Control no-store
       }
     }
     handle {
       respond 404
     }
   }
   '@ | Set-Content -LiteralPath $caddyfile -Encoding Ascii

   docker run --rm --detach --name keynest-preview-proxy --network keynest-test-local_default --publish 127.0.0.1:18080:8080 --mount "type=bind,source=$caddyfile,target=/etc/caddy/Caddyfile,readonly" caddy:2-alpine
   if ($LASTEXITCODE -ne 0) { throw 'Preview proxy could not start' }
   curl.exe --fail --silent --show-error --retry 5 --retry-connrefused http://127.0.0.1:18080/api/v1/tools/health
   if ($LASTEXITCODE -ne 0) { throw 'Preview proxy cannot reach the test API' }
   ```

3. Install [`cloudflared`](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/) and start a [Quick Tunnel](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/) in a second terminal. Keep it running and copy the `https://….trycloudflare.com` address it prints.

   ```powershell
   cloudflared tunnel --url http://127.0.0.1:18080
   ```

4. In the first terminal, substitute the tunnel address and the exact Preview web origin. Check for API JSON, a `404` on Actuator, and a successful `OPTIONS` response with `Access-Control-Allow-Origin` equal to your Preview origin.

   ```powershell
   $tunnel = 'https://your-assigned-subdomain.trycloudflare.com'
   $previewOrigin = 'https://keynest-test.example.com'
   $api = "$tunnel/api/v1"
   curl.exe --fail --silent --show-error "$api/tools/health"
   curl.exe -s -o NUL -w "%{http_code}\n" "$tunnel/actuator/health"
   curl.exe -i -X OPTIONS "$api/vault/items" -H "Origin: $previewOrigin" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: authorization, content-type"
   ```

5. In Vercel, set `KEYNEST_API_BASE_URL` for **Preview** on the test branch to the value of `$api`, including `/api/v1`. Redeploy that branch and sign in on its stable Preview URL, then open `/account#diagnostics-heading`. In DevTools, verify that the health request goes to the tunnel URL and returns JSON. Then verify sign-in and a Vault operation with disposable data. A new Quick Tunnel gives you a new URL, which requires another Preview build.
6. Stop the tunnel with **Ctrl+C**, then run `docker stop keynest-preview-proxy` and `Remove-Item -LiteralPath $caddyfile` in the first terminal. Remove the temporary Vercel branch override or restore its previous value. Redeploy the test branch if it should point to the restored API, and restore the local test stack’s previous CORS setting.

## API response headers and HTTPS ingress

For the separately hosted Angular document, see [production Web CSP delivery and proxy configuration](../security/controls/web-csp.md). API response headers alone do not enforce the Web document's policy.

Manager API applies the following baseline before its request handlers and rejection filters, including preflight, health, missing-route and servlet error responses. Reset and async dispatches retain the baseline. This policy covers the API server; Manager Web is a separate document origin and requires its own Angular-compatible CSP.

| Header | API value |
| --- | --- |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `no-referrer` |
| `X-Frame-Options` | `DENY` |
| `Content-Security-Policy` | `default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'` |

The API serves data, not an embeddable web UI. Its document CSP denies script, style, object, frame, form and base-URL capabilities; it does not block an authorized Angular client from fetching JSON or replace authentication/CORS. Any future API-hosted interactive documentation needs an explicit policy review. Existing content types, CORS, `Cache-Control: no-store`, download headers and rate-limit `Retry-After` remain authoritative. Preserve these response headers through every ingress response path; do not replace them with permissive values or append a conflicting second policy. The application cannot enforce what a downstream proxy sends to a browser. Compare direct loopback responses with actual ingress responses using disposable data.

**HSTS belongs to the HTTPS ingress operator.** The API deliberately does not emit `Strict-Transport-Security` based on request scheme or forwarded headers. On a verified HTTPS hostname, configure exactly one ingress HSTS header, initially with a short test lifetime (for example `max-age=300`), and verify both normal and error responses before increasing retention. Confirm valid certificates and HTTP-to-HTTPS redirects on that hostname. Apply this separately to the Web and API hostnames. Do not enable `includeSubDomains` or `preload` without reviewing every affected subdomain and the long-lived rollback consequences. Local HTTP development must remain usable. Do not trust arbitrary `Forwarded` or `X-Forwarded-Proto` values as proof of TLS.

The packaged/hosted Compose smoke checks the API baseline on its existing successful and rejected workflows and both allowed and denied CORS preflights. Integration tests additionally exercise servlet errors, reset/commit, async dispatch, bounds, throttling and AI-disabled responses. These checks do not certify a public ingress, Web CSP or HttpOnly-cookie migration. H1 remains open for Web CSP compatibility/enforcement and the ADR-0007 transport reassessment.

Rollback of this slice removes the header filter/registration and its associated assertions, returning to the previous API-header coverage; document the reopened hardening gap. It changes no vault data, keys, schema, session transport or CORS allowlist. HSTS rollback is separate: browsers retain a received policy until its lifetime expires, so restoring old application code does not remove it.

## Promote to production

1. Record the tested commit, image digest and browser results. After release approval, back up the existing production database and retain its vault key separately before applying a change. See the [restore rehearsal](database-migrations.md#automated-backuprestore-rehearsal).
2. Use the same Compose commands with `-p keynest-production`, the private production env file, and its loopback port. Set the tested image digest there and confirm readiness through loopback and the production HTTPS API.
3. Configure the Vercel **Production** `KEYNEST_API_BASE_URL` with the production API URL. For controlled cutovers, [disable the automatic assignment of production domains](https://vercel.com/docs/deployments/promoting-a-deployment), stage a Production build of the same tested revision, then promote it after the API is healthy. Verify the production web health page and sign-in.
4. Keep the previous image digest and Vercel deployment. If rollback is needed, confirm database compatibility before switching the API image. Restore the retained-key backup only with an approved recovery plan. Vercel can roll its frontend back to the previous deployment independently.
