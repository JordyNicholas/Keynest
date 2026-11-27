import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { apiBaseUrl } from './api-base-url.mjs';
import { readFile } from 'node:fs/promises';
import { installPolicy, productionOutput } from './web-security.mjs';

const scriptsDir = fileURLToPath(new URL('.', import.meta.url));
const ensurePolicy = spawnSync(process.execPath, [fileURLToPath(new URL('./ensure-default-policy.mjs', import.meta.url))], {
  stdio: 'inherit',
  cwd: scriptsDir,
});
if (ensurePolicy.status) {
  process.exit(ensurePolicy.status ?? 1);
}

let destination;
try {
  destination = apiBaseUrl(process.env.KEYNEST_API_BASE_URL);
} catch (error) {
  // Do not echo a rejected value, which could contain a mistakenly supplied credential.
  console.error(error.message);
  process.exit(1);
}

const cli = fileURLToPath(new URL('../node_modules/@angular/cli/bin/ng.js', import.meta.url));
const result = spawnSync(process.execPath, [
  cli, 'build', ...process.argv.slice(2),
  '--define', `KEYNEST_API_BASE_URL=${JSON.stringify(destination)}`,
], { stdio: 'inherit', cwd: fileURLToPath(new URL('..', import.meta.url)) });
if (result.error) {
  console.error('Unable to launch the Angular build');
}
if (result.status !== 0) process.exit(result.status ?? 1);
try {
  const root = fileURLToPath(new URL('..', import.meta.url));
  const config = JSON.parse(await readFile(new URL('../angular.json', import.meta.url), 'utf8'));
  const output = productionOutput(root, config, process.argv.slice(2));
  if (output) await installPolicy(output, destination);
} catch {
  console.error('Unable to bind production Web CSP to the built API destination');
  process.exit(1);
}
