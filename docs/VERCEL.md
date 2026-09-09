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
| Production / Vercel build | `src/environments/prod.ts` (swapped in via `angular.json` `fileReplacements`) | `https://keynest.dev/api/v1` (update if your API host differs) |
| Compose (browser on host) | usually same as local | `http://localhost:8080/api/v1` when the API is published on host port 8080; otherwise `http://<compose-host>:8080/api/v1` |

`ng serve` uses the **development** build (no file replacement) → `dev.ts`.  
`npm run build` / Vercel use the **production** build → `prod.ts`.

### CORS

When the SPA runs on a Vercel origin and the API on another host, the Manager API must allow that origin. Local `localhost:4200` → `localhost:8080` is the default dev path.
