import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { verifyCspBuild } from './verify-csp-build.mjs';
import { validatePolicy } from './web-security.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const build = fileURLToPath(new URL('./build.mjs', import.meta.url));
const output = new URL('../dist/manager-web/browser/', import.meta.url);
const exampleApi = 'https://api.example.invalid/keynest/api/v1';

function compile(destination, outputPath) {
  const env = { ...process.env };
  delete env.KEYNEST_API_BASE_URL;
  if (destination !== undefined) {
    env.KEYNEST_API_BASE_URL = destination;
  }
  const result = spawnSync(process.execPath, [build, ...(outputPath ? ['--output-path', outputPath] : [])], { cwd: root, env, stdio: 'inherit' });
  assert.equal(result.status, 0, 'production build must succeed');
  const directory = outputPath ? new URL('../' + outputPath + '/browser/', import.meta.url) : output;
  return readdirSync(directory).filter(name => name.endsWith('.js'))
    .map(name => readFileSync(new URL(name, directory), 'utf8')).join('\n');
}

const custom = compile(exampleApi);
assert.ok(custom.includes(exampleApi), 'custom API destination must be compiled into the bundle');
assert.ok(!custom.includes('REPLACE-WITH-RAILWAY'), 'old placeholder must be absent');
await verifyCspBuild(exampleApi);

const split = compile('http://localhost:8080/api/v1', 'dist/manager-web-split');
assert.ok(split.includes('http://localhost:8080/api/v1'), 'split-origin API must be compiled into its own output');
await verifyCspBuild('http://localhost:8080/api/v1', { splitOrigin: true });

// Leave the same-origin production artifact as the final output.
const sameOrigin = compile(undefined);
assert.ok(sameOrigin.includes('/api/v1'), 'same-origin API path must be present');
assert.ok(!sameOrigin.includes(exampleApi), 'custom destination must not persist between builds');
assert.ok(!sameOrigin.includes('REPLACE-WITH-RAILWAY'), 'old placeholder must be absent');
await verifyCspBuild(undefined);
const prepared = spawnSync(process.execPath, [fileURLToPath(new URL('../../../scripts/prepare-vercel-output.js', import.meta.url))], { stdio: 'inherit' });
assert.equal(prepared.status, 0, 'portable Build Output API preparation must succeed');
const policy = validatePolicy(JSON.parse(readFileSync(new URL('web-security.json', output), 'utf8')));
const routes = JSON.parse(readFileSync(new URL('../../../.vercel/output/config.json', import.meta.url), 'utf8')).routes;
assert.deepEqual(routes[0], { src: '/(.*)', headers: policy.responseHeaders, continue: true });
assert.equal(routes[1].handle, 'filesystem', 'security headers must precede filesystem and SPA routing');
console.log('Custom and same-origin production bundle configuration verified');
