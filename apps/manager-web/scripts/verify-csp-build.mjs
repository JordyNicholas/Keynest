import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, extname } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { apiBaseUrl } from './api-base-url.mjs';

// Candidate policy only: this fixture does not configure production hosting.
export async function verifyCspBuild(destination) {
  const api = apiBaseUrl(destination);
  const origin = api.startsWith('/') ? '' : new URL(api).origin;
  const policy = `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self' ${origin}; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'`;
  const root = resolve(fileURLToPath(new URL('../dist/manager-web/browser/', import.meta.url)));
  const profile = await mkdtemp(join(tmpdir(), 'keynest-csp-'));
  let forbiddenRequests = 0;
  const server = createServer(async (req, res) => {
    try {
      if (req.url.startsWith('/_forbidden')) { forbiddenRequests++;res.writeHead(500).end();return; }
      if (req.url === '/_probe.js') {
        res.setHeader('Content-Type', 'application/javascript');
        res.end(await readFile(new URL('./csp-browser-probe.js', import.meta.url)));return;
      }
      if (req.url === '/_allowed') { res.setHeader('Content-Type', 'application/json');res.end('{}');return; }
      const path = resolve(root, '.' + new URL(req.url, 'http://fixture.invalid').pathname);
      if (path !== root && !path.startsWith(root + '/')) { res.writeHead(404).end();return; }
      let data;
      try { data = await readFile(path); } catch { data = await readFile(join(root, 'index.html')); }
      if (path === root || extname(path) === '.html' || !extname(path)) {
        data = data.toString().replace('<head>', '<head><script src="/_probe.js"></script>');
        res.setHeader('Content-Type', 'text/html');
        res.setHeader('Content-Security-Policy', policy);
      } else res.setHeader('Content-Type', ({ '.js': 'application/javascript', '.css': 'text/css', '.ico': 'image/x-icon', '.svg': 'image/svg+xml' })[extname(path)] ?? 'application/octet-stream');
      res.setHeader('X-Content-Type-Options', 'nosniff');res.end(data);
    } catch { res.writeHead(500).end(); }
  });
  try {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = server.address().port;
    let stdout;
    try { ({ stdout } = await promisify(execFile)(process.env.CHROME_BIN || 'google-chrome', [
      '--headless', '--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking',
      '--no-first-run', `--user-data-dir=${profile}`, '--virtual-time-budget=15000', '--dump-dom',
      `http://127.0.0.1:${port}/`,
    ], { timeout: 45000, maxBuffer: 8 * 1024 * 1024 })); }
    catch { throw new Error('CSP browser fixture failed to complete (payload omitted)'); }
    // Never print DOM, generated passwords or browser diagnostics on failure.
    assert.ok(stdout.includes('KEYNEST_CSP_PASS'), 'production browser CSP probe failed (payload omitted)');
    assert.equal(forbiddenRequests, 0, 'blocked requests must not reach the fixture');
    console.log('Production CSP fixture: public tools/dialog and script/connection denials passed');
  } finally {
    await new Promise(resolve => server.close(resolve));
    await rm(profile, { recursive: true, force: true });
  }
}
