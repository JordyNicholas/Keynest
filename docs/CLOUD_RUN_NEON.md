# Cloud Run + Neon — Manager API host (recommended free path)

**UI host:** Vercel (Angular)  
**API host:** Google Cloud Run  
**DB:** Neon Postgres (free plan)

This replaces the Oracle VM experiment for day-to-day free hosting while you keep developing Keynest.

## Target hosts

| Role | Provider | Example |
| --- | --- | --- |
| UI | Vercel | `https://keynest-manager.vercel.app` |
| API | Cloud Run | `https://keynest-api-xxxxx-uc.a.run.app` |
| API base for Angular | `environment.apiBaseUrl` | `https://keynest-api-xxxxx-uc.a.run.app/api/v1` |
| DB | Neon | JDBC URL from Neon console |

> Do **not** point `apiBaseUrl` at the Vercel hostname. Vercel only serves the SPA.

## Prerequisites

- Google Cloud account (enable billing; set a **$0 budget alert**)
- `gcloud` CLI installed and logged in
- Docker (to build/push the image), or Cloud Build
- Neon account ([neon.tech](https://neon.tech)) — free plan, no card required for basic use

Prefer a Cloud Run **free-tier region**: `us-central1`, `us-east1`, or `us-west1`.

## 1. Create Neon database

1. Create a project (e.g. `keynest`).
2. Copy the connection string. Neon shows something like:

   ```text
   postgresql://USER:PASSWORD@ep-xxxx.region.aws.neon.tech/neondb?sslmode=require
   ```

3. Convert to **JDBC** for Spring (add `jdbc:` prefix):

   ```text
   jdbc:postgresql://ep-xxxx.region.aws.neon.tech/neondb?sslmode=require
   ```

4. Note **user** and **password** separately for env vars.

## 2. Build and push the API image

From the **repo root** (build context must be the monorepo root):

```bash
export PROJECT_ID=your-gcp-project-id
export REGION=us-central1
export REPO=keynest
export IMAGE=${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/manager-api:latest

gcloud config set project "$PROJECT_ID"
gcloud services enable run.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com

gcloud artifacts repositories create "$REPO" \
  --repository-format=docker \
  --location="$REGION" \
  --description="Keynest images" || true

gcloud auth configure-docker "${REGION}-docker.pkg.dev"

docker build -f deploy/Dockerfile.api -t "$IMAGE" .
docker push "$IMAGE"
```

Or build in GCP without local Docker:

```bash
gcloud builds submit --tag "$IMAGE" --dockerfile=deploy/Dockerfile.api .
```

(`gcloud builds submit` with Dockerfile path may need a `cloudbuild.yaml`; local `docker build` + push is fine.)

## 3. Deploy to Cloud Run

Generate a vault key once and reuse it (losing it makes existing envelopes unreadable):

```bash
export VAULT_KEY=$(openssl rand -hex 32)
echo "$VAULT_KEY"   # store securely
```

Deploy (replace placeholders):

```bash
gcloud run deploy keynest-api \
  --image="$IMAGE" \
  --region="$REGION" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --memory=512Mi \
  --cpu=1 \
  --min-instances=0 \
  --max-instances=2 \
  --set-env-vars="SPRING_PROFILES_ACTIVE=docker" \
  --set-env-vars="KEYNEST_JDBC_URL=jdbc:postgresql://ep-xxxx.region.aws.neon.tech/neondb?sslmode=require" \
  --set-env-vars="KEYNEST_DB_USER=neondb_owner" \
  --set-env-vars="KEYNEST_DB_PASSWORD=YOUR_NEON_PASSWORD" \
  --set-env-vars="KEYNEST_VAULT_KEY_HEX=${VAULT_KEY}" \
  --set-env-vars="KEYNEST_VAULT_KEY_ID=vault-v1" \
  --set-env-vars="KEYNEST_CORS_ALLOWED_ORIGINS=https://keynest-manager.vercel.app,http://localhost:4200"
```

Prefer **Secret Manager** for `KEYNEST_DB_PASSWORD` and `KEYNEST_VAULT_KEY_HEX` once comfortable; env vars are OK for first bring-up.

Cloud Run sets `PORT`; the app binds via `server.port=${PORT:8080}`.

## 4. Smoke test

```bash
SERVICE_URL=$(gcloud run services describe keynest-api --region="$REGION" --format='value(status.url)')
curl -sS "${SERVICE_URL}/api/v1/tools/health"
```

Expect `"status":"ok"`.

CORS check:

```bash
curl -i -X OPTIONS "${SERVICE_URL}/api/v1/tools/health" \
  -H "Origin: https://keynest-manager.vercel.app" \
  -H "Access-Control-Request-Method: GET"
```

Look for `Access-Control-Allow-Origin: https://keynest-manager.vercel.app`.

## 5. Point the Vercel UI at Cloud Run

After [#18](https://github.com/JordyNicholas/Keynest/issues/18) environments land (or on branch `KN#18`):

In `apps/manager-web/src/environments/prod.ts`:

```ts
apiBaseUrl: 'https://YOUR-SERVICE-xxxxx-uc.a.run.app/api/v1',
```

Redeploy Vercel. In the browser Network tab, API calls must hit the Cloud Run host — not `*.vercel.app/api/...`.

## 6. Stay in the free tier

- Region: `us-central1` / `us-east1` / `us-west1`
- `--min-instances=0` (scale to zero)
- Modest `--memory=512Mi` (raise to `1Gi` if OOM on cold start)
- Neon Free: ~0.5 GB storage; compute scales to zero when idle
- GCP: create a **budget alert at $1** so surprises are visible early

Cold starts after idle are normal on free serverless.

## Local Compose still works

Unchanged:

```bash
docker compose -f deploy/docker-compose.yml up --build
```

Uses `KEYNEST_DB_HOST=postgres` style vars. Cloud Run uses `KEYNEST_JDBC_URL` instead.

## Checklist

- [ ] Neon project + JDBC URL with `sslmode=require`
- [ ] Image in Artifact Registry
- [ ] Cloud Run service healthy on `/api/v1/tools/health`
- [ ] CORS includes Vercel origin
- [ ] Angular prod `apiBaseUrl` = Cloud Run `/api/v1`
- [ ] Budget alert set; vault key is not the demo hex
- [ ] Oracle micro stopped/terminated if unused
