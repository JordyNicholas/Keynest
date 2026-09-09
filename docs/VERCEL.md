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

## Hosts (production)

| Role | Host |
| --- | --- |
| **UI** | This Vercel project (e.g. `https://keynest-manager.vercel.app`) |
| **API** | Oracle Always Free VM — see [ORACLE_ALWAYS_FREE.md](./ORACLE_ALWAYS_FREE.md) |

Angular production builds should call:

```text
http://<ORACLE_PUBLIC_IP>:8080/api/v1
```

(or `https://api.<your-domain>/api/v1` after TLS). Never point `apiBaseUrl` at the Vercel hostname — Vercel does not run the Spring API.

After [#18](https://github.com/JordyNicholas/Keynest/issues/18), set that value in `apps/manager-web/src/environments/prod.ts`.

### CORS

The Oracle API must allow your Vercel origin via `KEYNEST_CORS_ALLOWED_ORIGINS` (documented in the Oracle guide). Local dev remains `http://localhost:4200`.
