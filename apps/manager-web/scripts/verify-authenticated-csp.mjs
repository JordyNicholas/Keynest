import assert from 'node:assert/strict';
import { verifyCspBuild } from './verify-csp-build.mjs';

assert.deepEqual(process.argv.slice(2), ['--disposable'], 'requires an explicitly disposable loopback Compose API');
await verifyCspBuild(undefined, { authenticated: true });
