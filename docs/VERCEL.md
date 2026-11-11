# Manager Web deployment configuration

Manager Web is a static Angular application and can use any static host or reverse proxy. Production defaults to `/api/v1` on the web origin. Split-origin hosting uses the public build variable `KEYNEST_API_BASE_URL`; no cloud-specific hostname or source edit is required.

The Git-connected Vercel project was created when this repo was a **Next.js** app. Keynest Manager Web is now **Angular** at `apps/manager-web`.

## Required dashboard changes

If the deploy fails with:

> No Next.js version detected. Make sure your package.json has "next" …

do **not** add `next` back. Fix the project preset instead:

| Setting | Value |
|---|---|
| Root Directory | `apps/manager-web` |
| Framework Preset | `Angular` (or Other) |
| Install Command | `npm ci` (default is fine) |
| Build Command | `npm run build` (default is fine) |
| Output Directory | `dist/manager-web/browser` |

Then **Redeploy** the latest commit.

`vercel.json` in the repo already matches these values when Root Directory is `apps/manager-web`.

## Manager API base URL (`API_BASE_URL`)

The Angular app reads `environment.apiBaseUrl`, provided as the `API_BASE_URL` injection token (`apps/manager-web/src/app/injectionTokens`). `npm run build` validates `KEYNEST_API_BASE_URL` and substitutes a string constant through Angular's build-time `define` option. The production environment falls back to `/api/v1` when built directly without a definition.

| Target | File / config | `apiBaseUrl` value |
| --- | --- | --- |
| Local `ng serve` | `src/environments/dev.ts` | `http://localhost:8080/api/v1` |
| Compose (browser on host) | same as local | `http://localhost:8080/api/v1` when API is on host port 8080 |
| Production, same origin | `npm run build` with no override | `/api/v1`; reverse proxy routes `/api/**` to Manager API |
| Production, separate API origin | `KEYNEST_API_BASE_URL=https://api.example.com/api/v1 npm run build` | Validated HTTPS URL compiled into the bundle |

`ng serve` → development build → `dev.ts`.  
`npm run build` / Vercel → production build → `prod.ts`.

The variable is public configuration: it must never contain a key, bearer, password, user information, query string, or fragment. The API path must end in `/api/v1`; an optional path prefix is supported. Root-relative paths are accepted; protocol-relative URLs are rejected. External APIs require HTTPS. Explicit HTTP loopback URLs (`localhost`, `127.0.0.1`, `[::1]`) are allowed for local build rehearsals only. Missing means the same-origin default; blank or malformed means a failed build. Validation errors do not print the rejected value.

On a same-origin deployment, route `/api/**` to Manager API before the SPA fallback. The supplied Vercel SPA rewrite does not install an API proxy: set an absolute API URL for that topology unless you deliberately configure a proxy. Route health probes privately as described in [operations](./OPERATIONS.md).

For Vercel or another build service, set `KEYNEST_API_BASE_URL` in that project's build environment and retain `npm run build`. Rebuild/redeploy after changing it; changing an environment variable after build does not update the browser bundle. Vercel web plus Railway API is one supported layout, not a core dependency. Supply only an operator-controlled API destination because the browser sends session bearers and vault requests there.

### CORS

For separate web/API origins, run the deployed API with Spring profiles `docker,prod` and set `KEYNEST_CORS_ALLOWED_ORIGINS` to the exact web origin (comma-separated if needed). Supply a randomly generated 32-byte hex `KEYNEST_VAULT_KEY_HEX` and a stable `KEYNEST_VAULT_KEY_ID`; keep `KEYNEST_ALLOW_DEMO_VAULT_KEY` unset/false. Local default remains `http://localhost:4200` → `http://localhost:8080`. Key requirements and recovery implications are documented in [ADR-0006](./ADR-0006-manager-vault-key-and-aad.md).

### Reverse proxies and rate-limit identity

Keynest ignores `X-Forwarded-For` by default and rate-limits the direct peer. For a hosted API behind a reverse proxy, set `KEYNEST_RATE_LIMIT_TRUSTED_PROXIES` only to IP/CIDR ranges the hosting provider documents as the direct proxy network. Keynest validates those ranges at startup and walks multi-hop chains from right to left. Do not configure all-address ranges such as `0.0.0.0/0` or `::/0`: that would allow direct callers to select their own limiter identity.

The built-in limiter is intentionally portable and per API process. Multiple replicas do not share counters; use a provider edge limiter or an external distributed adapter when globally consistent limits are required. No proxy setting is needed for direct local or Compose access.

### Local smoke test (#18)

1. `mvn -pl apps/manager-api spring-boot:run` → H2, port 8080  
2. `cd apps/manager-web && npm start` → `dev.ts` → `http://localhost:8080/api/v1`  
3. Open http://localhost:4200 and hit health / register / vault  
4. Production check: `npm run test:config` and `npm run test:build-config` verify URL rejection plus custom/default bundle substitution. The last build uses the same-origin default. Set your real public API URL and run `npm run build` again for a split-origin deploy.
