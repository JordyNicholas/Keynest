import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../src/app/ui/expiry-date.ts', import.meta.url), 'utf8');
const javascript = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
for (const [timezone, expected] of [
  ['America/Sao_Paulo', '2032-03-16T02:59:59.999Z'],
  ['America/New_York', '2032-03-16T03:59:59.999Z'],
  ['Europe/Berlin', '2032-03-15T22:59:59.999Z'],
  ['Etc/UTC', '2032-03-15T23:59:59.999Z'],
]) {
  test(`expiry preserves the local calendar day in ${timezone}`, () => {
    const result = spawnSync(process.execPath, ['-e', javascript + `
      const assert = require('node:assert/strict');
      const date = exports.localDateAfterDays(2, new Date(2032, 2, 13, 22));
      assert.equal(date.getDate(), 15);
      assert.equal(date.getHours(), 0);
      assert.equal(exports.expiryInstant(date), ${JSON.stringify(expected)});
      assert.equal(exports.expiryInstant(null), null);
    `], { env: { ...process.env, TZ: timezone }, encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
  });
}
