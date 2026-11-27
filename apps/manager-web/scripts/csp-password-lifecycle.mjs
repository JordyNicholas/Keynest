import assert from 'node:assert/strict';

/** Synthetic credentials only; report fixed stages, never input/response payloads. */
export async function passwordLifecycle(page, email, currentPassword, newPassword, apiBase, stage) {
  const original = await page.evaluate(() => sessionStorage.getItem('keynest.token'));
  const second = await page.evaluate(async (email, password, apiBase) => {
    const response = await fetch(apiBase + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
    return response.ok ? (await response.json()).token : null;
  }, email, currentPassword, apiBase);
  assert.ok(typeof second === 'string' && second.length > 0);
  stage('PASSWORD_ACCOUNT');await page.locator('nav a[href="/account"]').click();
  await page.waitForSelector('#open-change-password', { visible: true });await page.setViewport({ width: 390, height: 844 });
  stage('PASSWORD_DIALOG');await page.focus('#open-change-password');await page.keyboard.press('Enter');
  await page.waitForSelector('#change-current-password', { visible: true });
  await page.waitForFunction(() => document.activeElement?.id === 'change-current-password');
  assert.ok(await page.$eval('kn-change-password-dialog', node => node.scrollWidth <= node.clientWidth + 1));
  stage('PASSWORD_KEYBOARD');
  for (let index = 0; index < 3; index++) await page.keyboard.press('Tab');
  assert.ok(await page.evaluate(() => document.activeElement?.getAttribute('aria-controls')?.includes('change-new-password')));
  await page.keyboard.press('Space');
  await page.waitForFunction(() => ['change-current-password', 'change-new-password', 'change-confirm-password']
    .every(id => document.getElementById(id)?.type === 'text'));
  stage('PASSWORD_CANCEL');await page.keyboard.press('Escape');
  await page.waitForFunction(() => !document.querySelector('kn-change-password-dialog'));
  await page.waitForFunction(() => document.activeElement?.id === 'open-change-password');
  assert.ok(await page.evaluate(original => sessionStorage.getItem('keynest.token') === original, original));
  stage('PASSWORD_SUBMIT');await page.keyboard.press('Enter');
  await page.waitForSelector('#change-current-password', { visible: true });
  await page.locator('#change-current-password').fill(currentPassword);
  await page.locator('#change-new-password').fill(newPassword);await page.locator('#change-confirm-password').fill(newPassword);
  const response = page.waitForResponse(value => new URL(value.url()).pathname === '/api/v1/auth/password');
  await page.locator('kn-change-password-dialog button[type="submit"]').click();assert.equal((await response).status(), 200);
  stage('PASSWORD_REPLACEMENT');
  await page.waitForFunction(original => {
    const token = sessionStorage.getItem('keynest.token');return token && token !== original;
  }, {}, original);
  await page.waitForFunction(() => !document.querySelector('kn-change-password-dialog'));
  assert.ok(await page.$('.shell.authenticated'));
  stage('PASSWORD_PRIOR_AUTHORITY');
  assert.ok(await page.evaluate(async (tokens, email, password, apiBase) => {
    for (const token of tokens) {
      if ((await fetch(apiBase + '/auth/me', { method: 'POST', headers: { Authorization: 'Bearer ' + token } })).status !== 401) return false;
    }
    return (await fetch(apiBase + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) })).status === 401;
  }, [original, second], email, currentPassword, apiBase));
  await page.setViewport({ width: 1280, height: 900 });
}
