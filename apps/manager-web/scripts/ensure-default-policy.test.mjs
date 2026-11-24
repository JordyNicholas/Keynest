import assert from 'node:assert/strict';
import test from 'node:test';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('ensure-default-policy succeeds when the monorepo policy JSON is present', () => {
  const result = spawnSync(
    process.execPath,
    [fileURLToPath(new URL('./ensure-default-policy.mjs', import.meta.url))],
    { encoding: 'utf8' },
  );
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stderr, '');
});
