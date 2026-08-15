# Keynest

Password **tools** you can embed — and a **Manager** vault that uses them.

| Offering | Role |
|---|---|
| **Keynest Tools** | Java libs: `keynest-seal`, `keynest-policy`, `keynest-generate` |
| **Keynest Manager** | Angular UI + Spring Boot API (dogfoods the libs) |

> North star: [docs/PRODUCT.md](./docs/PRODUCT.md)

## Repo layout

```text
libs/keynest-policy      password standards + validate
libs/keynest-generate    CSPRNG generate under the same policy
libs/keynest-seal        Seal envelope (AES-256-GCM) + Argon2id hash/verify
apps/manager-api         Spring Boot façade over the libs
apps/manager-web         Angular Manager shell
deploy/                  Docker Compose + API Dockerfile
archive/next-prototype   legacy Next.js app (do not extend)
```

## Prerequisites

- JDK 21+, Maven 3.9+
- Node 20+ (Angular Manager UI)

## Build & test (libs + API)

```bash
mvn test
```

Run the API:

```bash
mvn -pl apps/manager-api spring-boot:run
```

Tools endpoints (dogfood):

- `GET  /api/v1/tools/health`
- `POST /api/v1/tools/generate`
- `POST /api/v1/tools/validate` `{ "password": "...", "usernameOrEmail": "..." }`
- `POST /api/v1/tools/seal` / `open`
- `POST /api/v1/tools/hash-password` / `verify-password`

## Manager UI

```bash
cd apps/manager-web
npm install
npm start
```

Open http://localhost:4200 (proxies calls to API on :8080).

## Docker (API)

```bash
docker compose -f deploy/docker-compose.yml up --build
```

## Security stance

Uniqueness is the **Keynest Seal** envelope/API, not a custom cipher. See [ADR-0003](./docs/ADR-0003-keynest-seal.md).

## Rename

Target GitHub name: **`keynest`** (Settings → General).
