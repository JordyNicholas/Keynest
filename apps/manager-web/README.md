# Develop Manager Web

Use Node.js 22 and npm. Start the API and web app following [local setup](../../docs/operators/local-setup.md). Run the commands below from `apps/manager-web`.

## Test

```sh
npm ci
npm run test:ci
```

For watch mode, run `npm test`. Set `CHROME_BIN` for a locally installed Chrome or Chromium executable. The component tests use mocked HTTP. The [packaged browser fixtures](../../docs/security/controls/web-csp-verification.md) exercise the production build with a disposable Manager API and PostgreSQL.

## Build

```sh
npm run build
npm run test:build-config
```

The production output is `dist/manager-web/browser`. The second command verifies same-origin and custom API builds. Use the [Web deployment guide](../../docs/operators/web-deployment.md) to select an API destination and serve the generated security headers.
