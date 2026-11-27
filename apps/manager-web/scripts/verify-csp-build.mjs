import assert from 'node:assert/strict';
import { createServer, request as apiRequest } from 'node:http';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, extname } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { apiBaseUrl } from './api-base-url.mjs';
import { policyFile, policyMeta, staticHeaders, validatePolicy } from './web-security.mjs';

// Candidate policy only: this fixture does not configure production hosting.
export async function verifyCspBuild(destination, { authenticated = false, reducedMotion = false, splitOrigin = false } = {}) {
  assert.ok(!authenticated || (splitOrigin ? destination === 'http://localhost:8080/api/v1' : destination === undefined),
    'disposable authenticated fixture requires its verified loopback topology');
  const root = resolve(fileURLToPath(new URL(splitOrigin ? '../dist/manager-web-split/browser/' : '../dist/manager-web/browser/', import.meta.url)));
  const index = await readFile(join(root, 'index.html'), 'utf8');
  const policy = validatePolicy(JSON.parse(await readFile(join(root, policyFile), 'utf8')));
  assert.equal(policy.apiBaseUrl, apiBaseUrl(destination), 'policy must match this build destination');
  assert.ok(index.includes(policyMeta(policy)), 'emitted HTML must contain its validated policy');
  assert.ok(index.indexOf(policyMeta(policy)) < index.search(/<(?:script|link|base)\b/i), 'policy must precede resources');
  assert.doesNotMatch(index, /\son[a-z]+\s*=/i, 'production HTML must not need inline event handlers');
  const profile = await mkdtemp(join(tmpdir(), 'keynest-csp-'));
  let forbiddenRequests = 0;
  const authOutcomes = {};
  const server = createServer(async (req, res) => {
    try {
      if (req.url === '/_frame-allowed') {
        res.setHeader('Content-Type', 'text/html');res.end('<body data-frame-control="allowed">Frame control</body>');return;
      }
      if (req.url === '/_frame-positive' || req.url === '/_frame-negative') {
        res.setHeader('Content-Type', 'text/html');
        res.end(`<iframe src="${req.url === '/_frame-positive' ? '/_frame-allowed' : '/'}"></iframe>`);return;
      }
      if (authenticated && !splitOrigin && req.url.startsWith('/api/v1/')) {
        // Fixed CI loopback target only. This is not a production proxy or CORS test.
        const headers = {};
        for (const name of ['content-type', 'authorization', 'accept']) {
          if (req.headers[name]) headers[name] = req.headers[name];
        }
        const upstream = apiRequest({ hostname: '127.0.0.1', port: 8080, path: req.url,
          method: req.method, headers, timeout: 15000 }, reply => {
          if (req.url === '/api/v1/auth/register') authOutcomes.register = reply.statusCode;
          if (req.url === '/api/v1/auth/login') authOutcomes.login = reply.statusCode;
          res.writeHead(reply.statusCode, reply.headers);reply.pipe(res);
        });
        upstream.on('timeout', () => upstream.destroy());
        upstream.on('error', () => { if (!res.headersSent) res.writeHead(502);res.end(); });
        req.pipe(upstream);return;
      }
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
        if (!authenticated) data = data.toString().replace(policyMeta(policy), policyMeta(policy) + '<script src="/_probe.js"></script>');
        res.setHeader('Content-Type', 'text/html');
        for (const [name, value] of Object.entries(staticHeaders)) res.setHeader(name, value);
      } else res.setHeader('Content-Type', ({ '.js': 'application/javascript', '.css': 'text/css', '.ico': 'image/x-icon', '.svg': 'image/svg+xml' })[extname(path)] ?? 'application/octet-stream');
      res.setHeader('X-Content-Type-Options', 'nosniff');res.end(data);
    } catch { res.writeHead(500).end(); }
  });
  try {
    await new Promise((resolve, reject) => {
      server.once('error', reject);server.listen(splitOrigin ? 4200 : 0, '127.0.0.1', resolve);
    });
    const port = server.address().port;
    let marker;
    if (authenticated) {
      const { authenticatedBrowser } = await import('./authenticated-csp-browser.mjs');
      marker = await authenticatedBrowser(`http://${splitOrigin ? 'localhost' : '127.0.0.1'}:${port}/`, profile, reducedMotion,
        splitOrigin ? destination : '/api/v1');
    } else {
      let stdout;
      try { ({ stdout } = await promisify(execFile)(process.env.CHROME_BIN || 'google-chrome', [
        '--headless', '--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking',
        '--no-first-run', `--user-data-dir=${profile}`, `--virtual-time-budget=${authenticated ? 45000 : 15000}`, '--dump-dom',
        `http://127.0.0.1:${port}/`,
      ], { timeout: 45000, maxBuffer: 8 * 1024 * 1024 })); }
      catch { throw new Error('CSP browser fixture failed to complete (payload omitted)'); }
      // Never print DOM, generated passwords or browser diagnostics on failure.
      marker = stdout.match(/KEYNEST_CSP_(PASS|FAIL_[A-Z_]+)/)?.[0];
    }
    if (authenticated) console.log('Disposable fixture auth status codes:', JSON.stringify(authOutcomes));
    assert.equal(marker, 'KEYNEST_CSP_PASS', 'production browser CSP probe failed (payload omitted)');
    assert.equal(forbiddenRequests, 0, 'blocked requests must not reach the fixture');
    console.log(authenticated ? `Production CSP fixture: ${splitOrigin ? 'split-origin' : 'same-origin'} real sign-in, Vault create/edit/rotate/delete, encrypted file download/restore/duplicate-ignore/reveal, framing, all-device revocation with fresh sign-in, and logout passed (${reducedMotion ? 'reduced' : 'normal'} motion)`
      : 'Production CSP fixture: public tools/dialog and script/connection denials passed');
  } finally {
    await new Promise(resolve => server.close(resolve));
    await rm(profile, { recursive: true, force: true });
  }
}
