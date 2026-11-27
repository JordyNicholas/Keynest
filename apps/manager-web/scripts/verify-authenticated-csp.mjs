import assert from 'node:assert/strict';
import { verifyCspBuild } from './verify-csp-build.mjs';

assert.ok(process.argv.length === 3 && process.argv[2] === '--disposable',
  'requires an explicitly disposable loopback Compose API');
for (const reducedMotion of [false, true]) await verifyCspBuild(undefined, { authenticated: true, reducedMotion });
for (const reducedMotion of [false, true]) await verifyCspBuild('http://localhost:8080/api/v1', { authenticated: true, reducedMotion, splitOrigin: true });
