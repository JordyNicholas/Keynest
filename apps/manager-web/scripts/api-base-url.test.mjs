import assert from 'node:assert/strict';
import test from 'node:test';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { apiBaseUrl } from './api-base-url.mjs';

test('defaults to the same-origin API', () => {
  assert.equal(apiBaseUrl(undefined), '/api/v1');
});

test('build rejects a credential-bearing destination without printing it', () => {
  const marker = 'build-config-redaction-sentinel';
  const result = spawnSync(process.execPath, [fileURLToPath(new URL('./build.mjs', import.meta.url))], {
    env: { ...process.env, KEYNEST_API_BASE_URL: `https://${marker}@api.example.com/api/v1` },
    encoding: 'utf8',
  });
  assert.equal(result.status, 1);
  assert.ok(!result.stdout.includes(marker) && !result.stderr.includes(marker));
  assert.ok(result.stderr.includes('KEYNEST_API_BASE_URL must be'));
});

for (const value of ['/api/v1', '/keynest/api/v1', 'https://api.example.com/api/v1',
  'https://api.example.com:8443/keynest/api/v1', 'http://localhost:8080/api/v1',
  'http://127.0.0.1:8080/api/v1', 'http://[::1]:8080/api/v1']) {
  test(`accepts explicit API destination ${value}`, () => {
    assert.equal(apiBaseUrl(value), value);
    assert.equal(apiBaseUrl(value + '/'), value);
  });
}

for (const value of ['', ' ', '/api/v1?token=x', '/api/v1#section', '//evil.example/api/v1',
  'https://user:password@api.example.com/api/v1', 'https://api.example.com/api/v1?x=y',
  'https://api.example.com/api/v1#fragment', 'http://api.example.com/api/v1',
  'javascript:alert(1)', 'ftp://api.example.com/api/v1', 'https://api.example.com',
  '/wrong/path', '/api/../api/v1', 'https://api.example.com/a/../api/v1',
  'https://api.example.com/%61pi/v1', 'https://api.example.com\\api/v1',
  ' https://api.example.com/api/v1', 'https://api.example.com/api/v1\n',
  'https://REPLACE-WITH-RAILWAY-API.up.railway.app/api/v1']) {
  test(`rejects unsafe or invalid API destination ${JSON.stringify(value)}`, () => {
    assert.throws(() => apiBaseUrl(value));
  });
}
