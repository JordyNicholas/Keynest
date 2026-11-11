# ManagerWeb

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.22.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
npm test
```

For a single headless run matching CI, install Chrome/Chromium and run:

```bash
npm ci
npm run test:ci
```

Set `CHROME_BIN` to your browser executable when Chrome is not found automatically. CI runs the tests before the production build and fails on a test failure. Coverage includes tab-session storage, auth-guard redirects, authenticated vault-list rendering and failure/recovery, and timed/manual secret concealment. These browser unit/component tests use mocked HTTP; they do not replace the full-stack browser rehearsal against Manager API and PostgreSQL.

## Running end-to-end tests

No end-to-end test target is configured yet. The release plan still requires a full-stack browser rehearsal against Manager API and PostgreSQL.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
