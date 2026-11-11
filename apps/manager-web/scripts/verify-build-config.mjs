import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const build = fileURLToPath(new URL('./build.mjs', import.meta.url));
const output = new URL('../dist/manager-web/browser/', import.meta.url);
const exampleApi = 'https://api.example.invalid/keynest/api/v1';

function compile(destination) {
  const env = { ...process.env };
  delete env.KEYNEST_API_BASE_URL;
  if (destination !== undefined) {
    env.KEYNEST_API_BASE_URL = destination;
  }
  const result = spawnSync(process.execPath, [build], { cwd: root, env, stdio: 'inherit' });
  assert.equal(result.status, 0, 'production build must succeed');
  return readdirSync(output).filter(name => name.endsWith('.js'))
    .map(name => readFileSync(new URL(name, output), 'utf8')).join('\n');
}

const custom = compile(exampleApi);
assert.ok(custom.includes(exampleApi), 'custom API destination must be compiled into the bundle');
assert.ok(!custom.includes('REPLACE-WITH-RAILWAY'), 'old placeholder must be absent');

// Leave the same-origin production artifact as the final output.
const sameOrigin = compile(undefined);
assert.ok(sameOrigin.includes('/api/v1'), 'same-origin API path must be present');
assert.ok(!sameOrigin.includes(exampleApi), 'custom destination must not persist between builds');
assert.ok(!sameOrigin.includes('REPLACE-WITH-RAILWAY'), 'old placeholder must be absent');
console.log('Custom and same-origin production bundle configuration verified');
