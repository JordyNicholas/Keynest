import assert from 'node:assert/strict';
import puppeteer from 'puppeteer-core';

/** Real-time, isolated automation; never return DOM, response bodies or credentials. */
export async function authenticatedBrowser(url, profile, reducedMotion) {
  let browser;
  let stage = 'LAUNCH';
  try {
    browser = await puppeteer.launch({
      ...(process.env.CHROME_BIN ? { executablePath: process.env.CHROME_BIN } : { channel: 'chrome' }),
      headless: true, pipe: true, dumpio: false, userDataDir: profile,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking'],
    });
    const page = await browser.newPage();
    page.setDefaultTimeout(15000);
    await page.setViewport({ width: 1280, height: 900 });
    await page.setBypassCSP(false);
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: reducedMotion ? 'reduce' : 'no-preference' }]);
    await page.evaluateOnNewDocument(() => {
      window.__fixtureViolations = [];
      document.addEventListener('securitypolicyviolation', event => window.__fixtureViolations.push(event.effectiveDirective));
    });
    stage = 'BOOT';await page.goto(url, { waitUntil: 'networkidle0' });
    await page.waitForSelector('#home-heading', { visible: true });
    stage = 'SETUP_ACCOUNT';
    const email = `csp-${crypto.randomUUID()}@example.invalid`;
    const password = 'Synthetic account password!42';
    assert.ok(await page.evaluate(async (email, password) => (await fetch('/api/v1/auth/register', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }),
    })).ok, email, password));
    stage = 'SIGN_IN';
    await page.click('kn-public-header a[href*="auth=signin"]');
    await page.waitForSelector('#auth-email', { visible: true });
    await page.type('#auth-email', email);await page.type('#auth-password', password);
    const login = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/auth/login');
    await page.click('[role="dialog"] button[type="submit"]');
    assert.ok((await login).ok());
    await page.waitForSelector('.shell.authenticated', { visible: true });
    await page.waitForSelector('[role="dialog"]', { hidden: true });
    assert.ok(await page.evaluate(() => !!sessionStorage.getItem('keynest.token')));
    stage = 'VAULT_CREATE';
    await page.click('nav a[href="/vault"]');
    await page.waitForSelector('a[href="/vault/new"]', { visible: true });await page.click('a[href="/vault/new"]');
    await page.waitForSelector('input[name="label"]', { visible: true });
    await page.type('input[name="label"]', 'CSP synthetic item');
    const secret = 'Q7!mZ3#rT8$vL2@pX9%w';await page.type('input[type="password"]', secret);
    stage = 'CALENDAR';
    await page.click('mat-datepicker-toggle button');
    await page.waitForSelector('mat-datepicker-content', { visible: true });
    await page.keyboard.press('Escape');await page.waitForSelector('mat-datepicker-content', { hidden: true });
    stage = 'SAVE';await page.click('form button[type="submit"]');
    await page.waitForSelector('.vault-card mat-expansion-panel-header', { visible: true });
    await page.click('.vault-card mat-expansion-panel-header');
    stage = 'REVEAL';await page.waitForSelector('.reveal-action', { visible: true });await page.click('.reveal-action');
    await page.waitForFunction(secret => document.querySelector('output.secret')?.textContent === secret, {}, secret);
    stage = 'CONCEAL';await page.click('.reveal-action');await page.waitForSelector('output.secret', { hidden: true });
    stage = 'LOGOUT';await page.click('.session-controls button');
    await page.waitForSelector('#home-heading', { visible: true });
    assert.ok(await page.evaluate(() => !sessionStorage.getItem('keynest.token') && !document.querySelector('.shell.authenticated')));
    stage = 'CSP';assert.ok(await page.evaluate(() => window.__fixtureViolations.length === 0));
    return 'KEYNEST_CSP_PASS';
  } catch {
    // Puppeteer errors can include supplied values; propagate only a fixed stage.
    return 'KEYNEST_CSP_FAIL_' + stage;
  } finally {
    if (browser) await browser.close().catch(() => {});
  }
}
