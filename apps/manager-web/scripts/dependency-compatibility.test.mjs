import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

const require = createRequire(import.meta.url);

async function fixture(t) {
  const directory = await mkdtemp(join(tmpdir(), 'keynest-dependency-test-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  return directory;
}

test('npm package preparation retains README, native-build and executable discovery', async (t) => {
  const directory = await fixture(t);
  await mkdir(join(directory, 'bin'));
  await writeFile(join(directory, 'package.json'), JSON.stringify({
    name: 'synthetic-keynest-fixture', version: '1.0.0', directories: { bin: 'bin' },
  }));
  await writeFile(join(directory, 'README.md'), 'Synthetic package documentation');
  await writeFile(join(directory, 'binding.gyp'), '{}');
  await writeFile(join(directory, 'bin', 'synthetic-tool'), '#!/usr/bin/env node\n');
  const PackageJson = require('@npmcli/package-json');
  const prepared = await PackageJson.prepare(directory, { steps: ['readme', 'gypfile', 'binDir'] });
  assert.equal(prepared.content.readme, 'Synthetic package documentation');
  assert.equal(prepared.content.gypfile, true);
  assert.equal(prepared.content.bin['synthetic-tool'], 'bin/synthetic-tool');
});

test('cache verification keeps referenced content, reclaims orphan content and removes its cache', async (t) => {
  const directory = await fixture(t);
  const cache = join(directory, 'cache');
  const cacache = require('cacache');
  const kept = Buffer.from('Synthetic retained cache entry');
  await cacache.put(cache, 'retained', kept);
  await cacache.put(cache, 'orphan', Buffer.from('Synthetic orphan cache entry'));
  await cacache.rm.entry(cache, 'orphan');
  const stats = await cacache.verify(cache);
  assert.equal(stats.verifiedContent, 1);
  assert.equal(stats.reclaimedCount, 1);
  assert.deepEqual((await cacache.get(cache, 'retained')).data, kept);
  await cacache.rm.all(cache);
  await assert.rejects(cacache.get(cache, 'retained'), { code: 'ENOENT' });
});

test('ignore-walk preserves exclusions, negation, hidden files and nested paths', async (t) => {
  const directory = await fixture(t);
  await mkdir(join(directory, 'nested'));
  for (const name of ['keep.txt', 'drop.txt', '.hidden', 'nested/keep.txt', 'nested/drop.txt']) {
    await writeFile(join(directory, name), 'Synthetic file');
  }
  await writeFile(join(directory, '.npmignore'), '*.txt\n!keep.txt\n');
  const walk = require('ignore-walk');
  const files = await walk({ path: directory, ignoreFiles: ['.npmignore'] });
  assert.ok(files.includes('keep.txt'));
  assert.ok(files.includes('nested/keep.txt'));
  assert.ok(files.includes('.hidden'));
  assert.ok(!files.includes('drop.txt'));
  assert.ok(!files.includes('nested/drop.txt'));
});

test('TUF delegation continues to enforce component patterns and path depth', () => {
  const { DelegatedRole } = require('@tufjs/models/dist/role.js');
  const role = new DelegatedRole({
    keyIDs: ['synthetic-key'], threshold: 1, name: 'fixture', terminating: false,
    paths: ['packages/*.tgz'],
  });
  assert.equal(role.isDelegatedPath('packages/example.tgz'), true);
  assert.equal(role.isDelegatedPath('packages/example.txt'), false);
  assert.equal(role.isDelegatedPath('private/example.tgz'), false);
  assert.equal(role.isDelegatedPath('packages/nested/example.tgz'), false);
});

test('SockJS retains its CommonJS UUID v4 transport and opens a real local session', async (t) => {
  const sockjsRequire = createRequire(require.resolve('sockjs'));
  const { v4, validate, version } = sockjsRequire('uuid');
  const identifier = v4();
  assert.equal(validate(identifier), true);
  assert.equal(version(identifier), 4);
  const sockjs = require('sockjs').createServer({ log: () => {}, disconnect_delay: 10 });
  const server = createServer();
  sockjs.installHandlers(server, { prefix: '/fixture' });
  const connected = new Promise((resolve) => sockjs.on('connection', (connection) => {
    resolve(connection);
    connection.close();
  }));
  t.after(() => new Promise((resolve, reject) => {
    server.closeAllConnections();
    server.close((error) => error ? reject(error) : resolve());
  }));
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const response = await fetch(`http://127.0.0.1:${server.address().port}/fixture/000/synthetic/xhr`, {
    method: 'POST', signal: AbortSignal.timeout(5_000),
  });
  assert.equal(response.status, 200);
  assert.equal(await response.text(), 'o\n');
  const connection = await Promise.race([
    connected,
    new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('SockJS session did not open')), 5_000);
      timeout.unref();
    }),
  ]);
  assert.equal(validate(connection.id), true);
  assert.equal(version(connection.id), 4);
});
