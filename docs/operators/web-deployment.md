# Manager Web deployment configuration

Manager Web is a static Angular application and can use any static host or reverse proxy. Production defaults to `/api/v1` on the web origin. Split-origin hosting uses the public build variable `KEYNEST_API_BASE_URL`; no cloud-specific hostname or source edit is required.

For separate Docker-backed test and production environments, follow the [Manager deployment workflow](deployment.md).

Manager Web source is in `apps/manager-web`. Use the Angular build and the configuration already committed for your Vercel root.

## Required dashboard changes

For a project rooted at `apps/manager-web`, use these settings:

| Setting | Value |
|---|---|
| Root Directory | `apps/manager-web` |
| Framework Preset | `Angular` (or Other) |
| Install Command | `npm ci` (default is fine) |
| Build Command | `npm run build` (default is fine) |
| Output Directory | `dist/manager-web/browser` |

The repository root also has a [Vercel configuration](../../vercel.json) for builds rooted there. The [app configuration](../../apps/manager-web/vercel.json) uses the corresponding app-relative output path. Select the configuration that matches your project root.

### Default policy dependency

The public homepage generator and checker import the canonical packaged policy at
`libs/keynest-policy/src/main/resources/policies/keynest.default.v1.json`. That file is source
data (not a Maven build product). The repo-root `.vercelignore` must keep that path available to
the Vercel upload; the web build runs `scripts/ensure-default-policy.mjs` first so a missing file
fails with an explicit message instead of an opaque Angular resolve error.

## Manager API base URL (`API_BASE_URL`)

The Angular app reads `environment.apiBaseUrl`, provided as the `API_BASE_URL` injection token (`apps/manager-web/src/app/injectionTokens`). `npm run build` validates `KEYNEST_API_BASE_URL` and substitutes a string constant through Angular's build-time `define` option. The supported production build also generates the [API-bound Web CSP and response-header artifact](../security/controls/web-csp.md).

| Target | File / config | `apiBaseUrl` value |
| --- | --- | --- |
| Local `ng serve` | `src/environments/dev.ts` | `http://localhost:8080/api/v1` |
| Compose (browser on host) | same as local | `http://localhost:8080/api/v1` when API is on host port 8080 |
| Production, same origin | `npm run build` with no override | `/api/v1`; reverse proxy routes `/api/**` to Manager API |
| Production, separate API origin | `KEYNEST_API_BASE_URL=https://api.example.com/api/v1 npm run build` | Validated HTTPS URL compiled into the bundle |

`ng serve` → development build → `dev.ts`.\
`npm run build` / Vercel → production build → `prod.ts`.

The variable is public configuration: it must never contain a key, bearer, password, user information, query string, or fragment. The API path must end in `/api/v1`; an optional path prefix is supported. Root-relative paths are accepted; protocol-relative URLs are rejected. External APIs require HTTPS. Explicit HTTP loopback URLs using `localhost` or `127.0.0.1` support local production-build rehearsals. The production CSP also constrains the supported hostname forms. Missing means the same-origin default; blank or malformed means a failed build. Validation errors do not print the rejected value.

On a same-origin deployment, route `/api/**` to Manager API before the SPA fallback. The supplied Vercel SPA rewrite does not install an API proxy: set an absolute API URL for that topology unless you deliberately configure a proxy. Route health probes privately as described in [operations](runtime.md).

For Vercel or another build service, set `KEYNEST_API_BASE_URL` in that project's build environment and retain `npm run build`. Rebuild/redeploy after changing it; changing an environment variable after build does not update the browser bundle. Vercel web plus Railway API is one supported layout, not a core dependency. Supply only an operator-controlled API destination because the browser sends session bearers and vault requests there.

### CORS

For separate web/API origins, run the deployed API with Spring profiles `docker,prod` and set `KEYNEST_CORS_ALLOWED_ORIGINS` to the exact web origin (comma-separated if needed). Supply a randomly generated 32-byte hex `KEYNEST_VAULT_KEY_HEX` and a stable `KEYNEST_VAULT_KEY_ID`; keep `KEYNEST_ALLOW_DEMO_VAULT_KEY` unset/false. Local default remains `http://localhost:4200` → `http://localhost:8080`. Key requirements and recovery implications are documented in [ADR-0006](../architecture/decisions/ADR-0006-manager-vault-key-and-aad.md).

### Optional same-host CORS diagnostic

For a browser-reachable test API, follow the [hosted test setup](deployment.md#deploy-and-verify-hosted-test) or the [temporary HTTPS walkthrough](deployment.md#test-vercel-preview-with-a-local-api). The loopback diagnostic below applies to a browser running on the Docker host and depends on that browser's local-network permissions.

Use an isolated test database. Set `KEYNEST_API_BASE_URL=http://127.0.0.1:8080/api/v1` for the Preview branch and redeploy. Use the origin of the preview you open, including its scheme and hostname without a path or trailing slash.

With the existing test PostgreSQL service running, recreate the API using the preview override. Use the same Compose project name you used to start the test stack.

```bash
export KEYNEST_PREVIEW_ORIGIN=https://your-preview.vercel.app
docker compose -p keynest-local -f deploy/docker-compose.yml -f compose.preview.yml up -d --no-deps manager-api
```

Check whether the local API accepts the preview origin with a login preflight. This request uses no credentials.

```bash
curl -i -X OPTIONS http://127.0.0.1:8080/api/v1/auth/login \
  -H "Origin: $KEYNEST_PREVIEW_ORIGIN" \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

For Windows PowerShell, set the variable in the same window that runs Compose. Use `curl.exe` and keep the preflight on one line. The `printenv` command displays only the running API's CORS allowlist.

```powershell
$env:KEYNEST_PREVIEW_ORIGIN = 'https://your-preview.vercel.app'
docker compose -p keynest-local -f deploy/docker-compose.yml -f compose.preview.yml up -d --no-deps manager-api
docker compose -p keynest-local -f deploy/docker-compose.yml -f compose.preview.yml exec -T manager-api printenv KEYNEST_CORS_ALLOWED_ORIGINS
curl.exe -i -X OPTIONS "http://127.0.0.1:8080/api/v1/auth/login" -H "Origin: $env:KEYNEST_PREVIEW_ORIGIN" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: content-type"
```

Expect a 2xx response and `Access-Control-Allow-Origin` equal to the preview origin. If that works, confirm that Edge DevTools shows requests to `http://127.0.0.1:8080/api/v1` from your preview. Open the preview directly and allow local network access if Edge prompts you. Drop the override and recreate the API using your usual Compose command when the rehearsal ends.

### Reverse proxies and rate-limit identity

Keynest ignores `X-Forwarded-For` by default and rate-limits the direct peer. For a hosted API behind a reverse proxy, set `KEYNEST_RATE_LIMIT_TRUSTED_PROXIES` only to IP/CIDR ranges the hosting provider documents as the direct proxy network. Keynest validates those ranges at startup and walks multi-hop chains from right to left. Do not configure all-address ranges such as `0.0.0.0/0` or `::/0`: that would allow direct callers to select their own limiter identity.

The built-in limiter is intentionally portable and per API process. Multiple replicas do not share counters; use a provider edge limiter or an external distributed adapter when globally consistent limits are required. No proxy setting is needed for direct local or Compose access.

### Local smoke test (#18)

1. Start Manager API using the [README commands](../../README.md#run-api). The local H2 configuration listens on port 8080.
2. `cd apps/manager-web && npm ci && npm start` → `dev.ts` → `http://localhost:8080/api/v1`
3. Open http://localhost:4200 and hit health / register / vault\
4. Production check: `npm run test:config` and `npm run test:build-config` verify URL rejection plus custom/default bundle substitution. The last build uses the same-origin default. Set your real public API URL and run `npm run build` again for a split-origin deploy.
