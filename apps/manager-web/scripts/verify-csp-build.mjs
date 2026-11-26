import assert from 'node:assert/strict';
import { createServer, request as apiRequest } from 'node:http';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, extname } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { apiBaseUrl } from './api-base-url.mjs';

// Candidate policy only: this fixture does not configure production hosting.
export async function verifyCspBuild(destination, { authenticated = false } = {}) {
  assert.ok(!authenticated || destination === undefined, 'disposable authenticated fixture requires same-origin output');
  const api = apiBaseUrl(destination);
  const origin = api.startsWith('/') ? '' : new URL(api).origin;
  const policy = `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self' ${origin}; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'`;
  const root = resolve(fileURLToPath(new URL('../dist/manager-web/browser/', import.meta.url)));
  const index = await readFile(join(root, 'index.html'), 'utf8');
  assert.doesNotMatch(index, /\son[a-z]+\s*=/i, 'production HTML must not need inline event handlers');
  const profile = await mkdtemp(join(tmpdir(), 'keynest-csp-'));
  let forbiddenRequests = 0;
  const server = createServer(async (req, res) => {
    try {
      if (authenticated && req.url.startsWith('/api/v1/')) {
        // Fixed CI loopback target only. This is not a production proxy or CORS test.
        const headers = {};
        for (const name of ['content-type', 'authorization', 'accept']) {
          if (req.headers[name]) headers[name] = req.headers[name];
        }
        const upstream = apiRequest({ hostname: '127.0.0.1', port: 8080, path: req.url,
          method: req.method, headers, timeout: 15000 }, reply => {
          res.writeHead(reply.statusCode, reply.headers);reply.pipe(res);
        });
        upstream.on('timeout', () => upstream.destroy());
        upstream.on('error', () => { if (!res.headersSent) res.writeHead(502);res.end(); });
        req.pipe(upstream);return;
      }
      if (req.url.startsWith('/_forbidden')) { forbiddenRequests++;res.writeHead(500).end();return; }
      if (req.url === '/_probe.js') {
        res.setHeader('Content-Type', 'application/javascript');
        res.end(await readFile(new URL(authenticated ? './csp-authenticated-probe.js' : './csp-browser-probe.js', import.meta.url)));return;
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
      '--no-first-run', `--user-data-dir=${profile}`, `--virtual-time-budget=${authenticated ? 45000 : 15000}`, '--dump-dom',
      `http://127.0.0.1:${port}/`,
    ], { timeout: 45000, maxBuffer: 8 * 1024 * 1024 })); }
    catch { throw new Error('CSP browser fixture failed to complete (payload omitted)'); }
    // Never print DOM, generated passwords or browser diagnostics on failure.
    const marker = stdout.match(/KEYNEST_CSP_(PASS|FAIL_[A-Z_]+)/)?.[0];
    assert.equal(marker, 'KEYNEST_CSP_PASS', 'production browser CSP probe failed (payload omitted)');
    assert.equal(forbiddenRequests, 0, 'blocked requests must not reach the fixture');
    console.log(authenticated ? 'Production CSP fixture: real sign-in, Vault create/calendar/reveal/conceal and logout passed'
      : 'Production CSP fixture: public tools/dialog and script/connection denials passed');
  } finally {
    await new Promise(resolve => server.close(resolve));
    await rm(profile, { recursive: true, force: true });
  }
}
