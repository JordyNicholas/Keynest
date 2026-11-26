import assert from 'node:assert/strict';
import puppeteer from 'puppeteer-core';

/** Real-time, isolated automation; never return DOM, response bodies or credentials. */
export async function authenticatedBrowser(url, profile, reducedMotion, apiBase = '/api/v1') {
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
    stage = 'FRAME_CONTROL';
    await page.goto(url + '_frame-positive', { waitUntil: 'networkidle0' });
    await page.waitForFunction(() => document.querySelector('iframe')?.contentDocument?.body?.dataset.frameControl === 'allowed');
    stage = 'FRAME_DENIAL';
    await page.goto(url + '_frame-negative', { waitUntil: 'networkidle0' });
    await page.waitForFunction(() => {
      const frame = document.querySelector('iframe');
      return frame && (!frame.contentDocument || frame.contentDocument.URL.startsWith('chrome-error:'));
    });
    stage = 'BOOT';await page.goto(url, { waitUntil: 'networkidle0' });
    await page.waitForSelector('#home-heading', { visible: true });
    stage = 'SETUP_ACCOUNT';
    const email = `csp-${crypto.randomUUID()}@example.invalid`;
    const password = 'Synthetic account password!42';
    assert.ok(await page.evaluate(async (email, password, apiBase) => (await fetch(apiBase + '/auth/register', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }),
    })).ok, email, password, apiBase));
    stage = 'SIGN_IN';
    await page.locator('kn-public-header a[href*="auth=signin"]').click();
    await page.waitForSelector('#auth-email', { visible: true });
    await page.locator('#auth-email').fill(email);await page.locator('#auth-password').fill(password);
    const login = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/auth/login');
    await page.locator('[role="dialog"] button[type="submit"]').click();
    assert.ok((await login).ok());
    await page.waitForSelector('.shell.authenticated', { visible: true });
    await page.waitForSelector('[role="dialog"]', { hidden: true });
    assert.ok(await page.evaluate(() => !!sessionStorage.getItem('keynest.token')));
    stage = 'VAULT_NAV';
    await page.locator('nav a[href="/vault"]').click();
    stage = 'VAULT_NEW';
    await page.waitForSelector('a[href="/vault/new"]', { visible: true });await page.locator('a[href="/vault/new"]').click();
    stage = 'VAULT_LABEL';
    await page.waitForSelector('input[name="label"]', { visible: true });
    await page.locator('input[name="label"]').fill('CSP synthetic item');
    stage = 'VAULT_PASSWORD';
    const secret = 'Q7!mZ3#rT8$vL2@pX9%w';await page.locator('input[type="password"]').fill(secret);
    stage = 'CALENDAR';
    await page.locator('mat-datepicker-toggle button').click();
    await page.waitForSelector('mat-datepicker-content', { visible: true });
    await page.keyboard.press('Escape');await page.waitForSelector('mat-datepicker-content', { hidden: true });
    stage = 'SAVE';await page.locator('form button[type="submit"]').click();
    await page.waitForSelector('.vault-card mat-expansion-panel-header', { visible: true });
    await page.locator('.vault-card mat-expansion-panel-header').click();
    stage = 'REVEAL';await page.waitForSelector('.reveal-action', { visible: true });await page.locator('.reveal-action').click();
    await page.waitForFunction(secret => document.querySelector('output.secret')?.textContent === secret, {}, secret);
    stage = 'CONCEAL';await page.locator('.reveal-action').click();await page.waitForSelector('output.secret', { hidden: true });
    stage = 'LOGOUT';await page.locator('.session-controls button').click();
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
