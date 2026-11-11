# Vercel project settings (Manager Web)

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

The Angular app does **not** hardcode the API host in components. It reads `environment.apiBaseUrl`, provided as the `API_BASE_URL` injection token (`apps/manager-web/src/app/injectionTokens`).

| Target | File / config | `apiBaseUrl` value |
| --- | --- | --- |
| Local `ng serve` | `src/environments/dev.ts` | `http://localhost:8080/api/v1` |
| Compose (browser on host) | same as local | `http://localhost:8080/api/v1` when API is on host port 8080 |
| Production / Vercel | `src/environments/prod.ts` (via `angular.json` `fileReplacements`) | Your Railway HTTPS API + `/api/v1` (replace the placeholder before deploy) |

`ng serve` → development build → `dev.ts`.  
`npm run build` / Vercel → production build → `prod.ts`.

Intended production layout:

- **UI:** Vercel  
- **API + Postgres:** Railway (public API URL goes into `prod.ts`)

### CORS

When the SPA is on Vercel and the API on Railway, run the API with Spring profiles `docker,prod` and set `KEYNEST_CORS_ALLOWED_ORIGINS` to your Vercel origin (comma-separated if needed). Supply a randomly generated 32-byte hex `KEYNEST_VAULT_KEY_HEX` and a stable `KEYNEST_VAULT_KEY_ID`; keep `KEYNEST_ALLOW_DEMO_VAULT_KEY` unset/false. Local default remains `http://localhost:4200` → `http://localhost:8080`. Key requirements and recovery implications are documented in [ADR-0006](./ADR-0006-manager-vault-key-and-aad.md).

### Reverse proxies and rate-limit identity

Keynest ignores `X-Forwarded-For` by default and rate-limits the direct peer. For a hosted API behind a reverse proxy, set `KEYNEST_RATE_LIMIT_TRUSTED_PROXIES` only to IP/CIDR ranges the hosting provider documents as the direct proxy network. Keynest validates those ranges at startup and walks multi-hop chains from right to left. Do not configure all-address ranges such as `0.0.0.0/0` or `::/0`: that would allow direct callers to select their own limiter identity.

The built-in limiter is intentionally portable and per API process. Multiple replicas do not share counters; use a provider edge limiter or an external distributed adapter when globally consistent limits are required. No proxy setting is needed for direct local or Compose access.

### Local smoke test (#18)

1. `mvn -pl apps/manager-api spring-boot:run` → H2, port 8080  
2. `cd apps/manager-web && npm start` → `dev.ts` → `http://localhost:8080/api/v1`  
3. Open http://localhost:4200 and hit health / register / vault  
4. Production check: `npm run build` then confirm the bundle uses `prod.ts` (no `localhost:8080` in the API base URL)
