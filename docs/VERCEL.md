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
