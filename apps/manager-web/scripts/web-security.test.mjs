import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { installPolicy, policyFile, policyMeta, productionOutput, securityPolicy, staticHeaders, validatePolicy } from './web-security.mjs';

test('build destination produces a strict, bounded connection policy', () => {
  const local = securityPolicy();
  assert.match(local.document, /script-src 'self';/);
  assert.match(local.document, /connect-src 'self';/);
  assert.doesNotMatch(local.document, /unsafe-eval|frame-ancestors|report-uri/);
  const split = securityPolicy('https://api.example.invalid/keynest/api/v1');
  assert.match(split.document, /connect-src 'self' https:\/\/api.example.invalid;/);
  assert.doesNotMatch(split.document, /https:;|\*/);
  assert.match(split.responseHeaders['Content-Security-Policy'], /frame-ancestors 'none'/);
});

test('CSP/HTML delimiter and credential inputs are rejected without value disclosure', () => {
  for (const value of ['https://user:sentinel@example.invalid/api/v1', 'https://api;sentinel.invalid/api/v1',
    'https://api\"sentinel.invalid/api/v1', "https://api'sentinel.invalid/api/v1", 'javascript:sentinel']) {
    assert.throws(() => securityPolicy(value), error => !error.message.includes('sentinel'));
  }
  for (const value of ['http://[::1]:8080/api/v1', 'https://192.0.2.1/api/v1', 'https://*.example.invalid/api/v1',
    'https://api_test.example.invalid/api/v1', 'https://api.example.invalid./api/v1']) assert.throws(() => securityPolicy(value));
  assert.match(securityPolicy('http://127.0.0.1:8080/api/v1').document, /http:\/\/127.0.0.1:8080/);
});

test('tampered or incomplete metadata cannot supply weaker deployment headers', () => {
  const policy = securityPolicy();
  assert.equal(validatePolicy(policy), policy);
  for (const change of [{ ...policy, document: "default-src *" }, { ...policy, responseHeaders: {} },
    { ...policy, extra: true }, { ...policy, schemaVersion: 2 }]) assert.throws(() => validatePolicy(change));
});

test('policy precedes every resource, and pre-existing policies are not silently replaced', async () => {
  const root = await mkdtemp(join(tmpdir(), 'keynest-policy-test-'));
  try {
    await writeFile(join(root, 'index.html'), '<html><head><base href="/"><script src="main.js"></script></head></html>');
    await installPolicy(root, 'https://api.example.invalid/api/v1');
    const policy = validatePolicy(JSON.parse(await readFile(join(root, policyFile), 'utf8')));
    const html = await readFile(join(root, 'index.html'), 'utf8');
    assert.ok(html.indexOf(policyMeta(policy)) < html.indexOf('<base'));
    await assert.rejects(installPolicy(root), /pre-existing CSP/);
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('both supported Vercel roots preserve the same mandatory response headers', async () => {
  for (const path of ['../../../vercel.json', '../vercel.json']) {
    const config = JSON.parse(await readFile(new URL(path, import.meta.url), 'utf8'));
    const expected = Object.entries(staticHeaders).map(([key, value]) => ({ key, value }));
    assert.deepEqual(config.headers, [{ source: '/(.*)', headers: expected }]);
    assert.deepEqual(config.rewrites, [{ source: '/(.*)', destination: '/index.html' }]);
  }
});

test('production output overrides are honored while explicit development remains separate', async () => {
  const config = JSON.parse(await readFile(new URL('../angular.json', import.meta.url), 'utf8'));
  assert.equal(productionOutput('/app', config, []), '/app/dist/manager-web/browser');
  assert.equal(productionOutput('/app', config, ['--output-path', 'custom']), '/app/custom/browser');
  assert.equal(productionOutput('/app', config, ['--output-path=custom']), '/app/custom/browser');
  assert.equal(productionOutput('/app', config, ['-c', 'development']), null);
});
