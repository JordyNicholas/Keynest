import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { apiBaseUrl } from './api-base-url.mjs';

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
process.exit(result.status ?? 1);
