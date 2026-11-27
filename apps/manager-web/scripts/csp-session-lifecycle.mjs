import assert from 'node:assert/strict';

/** Synthetic account only. Keep bearer/credential values out of output and error messages. */
export async function sessionLifecycle(page, email, password, apiBase, stage) {
  const original = await page.evaluate(() => sessionStorage.getItem('keynest.token'));
  const other = await page.evaluate(async (email, password, apiBase) => {
    const response = await fetch(apiBase + '/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }),
    });
    return response.ok ? (await response.json()).token : null;
  }, email, password, apiBase);
  assert.ok(typeof other === 'string' && other.length > 0);
  stage('DEVICES_DIALOG');
  stage('DEVICES_ACCOUNT_NAV');await page.locator('nav a[href="/account"]').click();
  stage('DEVICES_ACCOUNT_READY');await page.waitForSelector('#open-sign-out-devices', { visible: true });
  await page.setViewport({ width: 390, height: 844 });
  stage('DEVICES_OPEN');await page.focus('#open-sign-out-devices');await page.keyboard.press('Enter');
  stage('DEVICES_FIELD');await page.waitForSelector('#sign-out-devices-password', { visible: true });
  stage('DEVICES_INITIAL_FOCUS');await page.waitForFunction(() => document.activeElement?.id === 'sign-out-devices-password');
  stage('DEVICES_WIDTH');assert.ok(await page.$eval('kn-sign-out-devices-dialog', node => node.scrollWidth <= node.clientWidth + 1));
  stage('DEVICES_TAB_FOCUS');await page.keyboard.press('Tab');
  assert.ok(await page.evaluate(() => document.activeElement?.getAttribute('aria-controls') === 'sign-out-devices-password'));
  stage('DEVICES_SHOW_PASSWORD_KEY');await page.keyboard.press('Space');
  stage('DEVICES_SHOW_PASSWORD_RENDER');await page.waitForFunction(() => document.querySelector('#sign-out-devices-password')?.type === 'text');
  stage('DEVICES_CANCEL');await page.keyboard.press('Escape');
  await page.waitForSelector('kn-sign-out-devices-dialog', { hidden: true });
  stage('DEVICES_RETURN_FOCUS');await page.waitForFunction(() => document.activeElement?.id === 'open-sign-out-devices');
  stage('DEVICES_CANCEL_PRESERVES_SESSION');assert.ok(await page.evaluate(original => sessionStorage.getItem('keynest.token') === original, original));
  stage('DEVICES_REVOKE');
  await page.keyboard.press('Enter');await page.waitForSelector('#sign-out-devices-password', { visible: true });
  await page.locator('#sign-out-devices-password').fill(password);
  const revoked = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/auth/logout-all');
  await page.locator('kn-sign-out-devices-dialog button[type="submit"]').click();
  assert.equal((await revoked).status(), 200);
  await page.waitForSelector('#home-heading', { visible: true });
  assert.ok(await page.evaluate(() => !sessionStorage.getItem('keynest.token') && !document.querySelector('kn-sign-out-devices-dialog')));
  assert.ok(await page.evaluate(async (tokens, apiBase) => {
    for (const token of tokens) {
      const response = await fetch(apiBase + '/auth/me', { method: 'POST', headers: { Authorization: 'Bearer ' + token } });
      if (response.status !== 401) return false;
    }
    return true;
  }, [original, other], apiBase));
  stage('DEVICES_FRESH_SIGN_IN');
  await page.setViewport({ width: 1280, height: 900 });
  await page.locator('kn-public-header a[href*="auth=signin"]').click();
  await page.waitForSelector('#auth-email', { visible: true });
  await page.locator('#auth-email').fill(email);await page.locator('#auth-password').fill(password);
  await page.locator('[role="dialog"] button[type="submit"]').click();
  await page.waitForSelector('.shell.authenticated', { visible: true });
  await page.waitForSelector('[role="dialog"]', { hidden: true });
}
