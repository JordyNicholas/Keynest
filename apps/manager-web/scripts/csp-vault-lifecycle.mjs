import assert from 'node:assert/strict';
import { mkdir, open } from 'node:fs/promises';
import { join } from 'node:path';

// Synthetic disposable records only. The caller suppresses errors and removes the
// entire private browser profile (including downloads) after closing Chrome.
export async function vaultLifecycle(page, browser, profile, accountPassword, stage) {
  const card = '.vault-card';
  const header = `${card} mat-expansion-panel-header`;
  const secretOutput = `${card} output.secret`;
  const button = name => `::-p-aria(${name}[role="button"])`;
  let applyRequests = 0;
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/vault/restores/apply') applyRequests++;
  });
  const itemId = async () => new URL(await page.$eval(`${card} a[aria-label^="Edit details"]`, link => link.href)).pathname.split('/').at(-1);
  const expand = async () => {
    await page.waitForSelector(header, { visible: true });
    await page.locator(header).click();
    await page.waitForSelector(`${card} a.rotate-action`, { visible: true });
  };
  const reveal = async expected => {
    await page.locator(`${card} .reveal-action`).click();
    await page.waitForFunction(expected => document.querySelector('.vault-card output.secret')?.textContent === expected, {}, expected);
    await page.locator(`${card} .reveal-action`).click();
    await page.waitForSelector(secretOutput, { hidden: true });
  };
  const response = (path, method) => page.waitForResponse(reply => new URL(reply.url()).pathname === path && reply.request().method() === method);
  const clickResponse = async (selector, path, method) => {
    const [reply] = await Promise.all([response(path, method), page.locator(selector).click()]);
    assert.ok(reply.ok());
    return reply;
  };
  const confirmClick = async (selector, message) => {
    let timer;
    let listener;
    const confirmed = new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(new Error('Expected confirmation was unavailable')), 15000);
      listener = async dialog => {
        try {
          assert.equal(dialog.type(), 'confirm');assert.equal(dialog.message(), message);
          await dialog.accept();resolve();
        } catch { await dialog.dismiss().catch(() => {});reject(new Error('Unexpected confirmation')); }
      };
      page.once('dialog', listener);
    });
    try { await Promise.all([confirmed, page.locator(selector).click()]); }
    finally { clearTimeout(timer);page.off('dialog', listener); }
  };

  stage('EDIT');
  const originalId = await itemId();
  await page.locator(`${card} a[aria-label^="Edit details"]`).click();
  await page.locator('input[name="label"]').fill('CSP edited item');
  await page.locator('input[name="username"]').fill('csp-user');
  await page.locator('input[name="url"]').fill('https://example.invalid/login');
  await clickResponse('form button[type="submit"]', `/api/v1/vault/items/${originalId}`, 'PUT');
  await expand();
  assert.equal(await itemId(), originalId);
  assert.ok(await page.$eval(card, node => node.textContent.includes('CSP edited item')
    && node.textContent.includes('csp-user') && node.textContent.includes('https://example.invalid/login')));

  stage('ROTATION_GENERATE');
  await page.locator(`${card} a.rotate-action`).click();
  await page.locator('details.rotation-generator summary').click();
  await page.locator(button('Generate replacement')).click();
  await page.waitForFunction(() => document.querySelector('#rotation-password')?.value.length === 20);
  const replacement = await page.$eval('#rotation-password', input => input.value);
  assert.equal(await page.$eval('#rotation-password', input => input.type), 'password');
  await page.locator(button('Show password')).click();
  assert.equal(await page.$eval('#rotation-password', input => input.type), 'text');
  await page.locator(button('Hide password')).click();
  stage('ROTATION_SAVE');
  const [rotated] = await Promise.all([
    response(`/api/v1/vault/items/${originalId}`, 'PUT'),
    confirmClick('#rotation-form button[type="submit"]', 'Replace the saved password for this vault item? Confirm after changing it at the account website.'),
  ]);
  assert.ok(rotated.ok());
  await expand();assert.equal(await itemId(), originalId);
  await reveal(replacement);

  stage('EXPORT_REVIEW');
  await page.locator(button('Export')).click();
  await clickResponse('kn-vault-export-dialog button:not([mat-dialog-close])', '/api/v1/vault/exports/preview', 'POST');
  await page.locator('#export-account-password').fill(accountPassword);
  const passphrase = 'Synthetic backup phrase!42';
  await page.locator('#export-passphrase').fill(passphrase);
  await page.locator('#export-passphrase-confirmation').fill(passphrase);

  stage('EXPORT_DOWNLOAD');
  const downloadDir = join(profile, 'downloads');await mkdir(downloadDir, { mode: 0o700 });
  const client = await browser.target().createCDPSession();
  let downloadId;
  let timer;
  let started;
  let progress;
  try {
    await client.send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: downloadDir, eventsEnabled: true });
    const downloaded = new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(new Error('Encrypted download did not finish')), 30000);
      started = event => {
        if (event.suggestedFilename !== 'keynest-vault.keynest' || downloadId) { reject(new Error('Unexpected download'));return; }
        downloadId = event.guid;
      };
      progress = event => {
        if (event.guid !== downloadId) return;
        if (event.state === 'completed') resolve();
        if (event.state === 'canceled') reject(new Error('Encrypted download was canceled'));
      };
      client.on('Browser.downloadWillBegin', started);client.on('Browser.downloadProgress', progress);
    });
    await Promise.all([downloaded, clickResponse('kn-vault-export-dialog form button[type="submit"]', '/api/v1/vault/exports/download', 'POST')]);
  } finally {
    clearTimeout(timer);
    if (started) client.off('Browser.downloadWillBegin', started);
    if (progress) client.off('Browser.downloadProgress', progress);
    await client.send('Browser.setDownloadBehavior', { behavior: 'deny' }).catch(() => {});
    await client.detach();
  }
  const file = join(downloadDir, 'keynest-vault.keynest');
  const encrypted = await open(file, 'r');
  try {
    const size = (await encrypted.stat()).size;
    assert.ok(size > 79 && size <= 8 * 1024 * 1024 + 79);
    const prefix = Buffer.alloc(10);
    try {
      assert.equal((await encrypted.read(prefix, 0, prefix.length, 0)).bytesRead, 10);
      assert.ok(prefix.equals(Buffer.from([75, 69, 89, 78, 69, 83, 84, 66, 1, 1])));
    } finally { prefix.fill(0); }
  } finally { await encrypted.close(); }
  await page.waitForFunction(() => document.querySelector('kn-vault-export-dialog')?.textContent.includes('Encrypted file download started.'));
  assert.ok(await page.evaluate(() => ['export-account-password', 'export-passphrase', 'export-passphrase-confirmation']
    .every(id => !document.getElementById(id)?.value)));
  await page.locator('kn-vault-export-dialog [mat-dialog-close]').click();
  await page.waitForSelector('kn-vault-export-dialog', { hidden: true });

  stage('DELETE_SOURCE');
  const [deleted] = await Promise.all([
    response(`/api/v1/vault/items/${originalId}`, 'DELETE'),
    confirmClick(`${card} .delete-button`, 'Permanently delete this vault item?'),
  ]);
  assert.ok(deleted.ok());
  await page.waitForFunction(() => document.querySelector('.result-count')?.textContent.includes('0 matching items'));
  assert.equal(await page.$$eval(card, nodes => nodes.length), 0);

  const review = async () => {
    await page.locator(button('Restore')).click();
    const picker = await page.waitForSelector('#restore-file');await picker.uploadFile(file);
    await page.locator('#restore-passphrase').fill(passphrase);
    const reply = await clickResponse('kn-vault-restore-dialog form button[type="submit"]', '/api/v1/vault/restores/preview', 'POST');
    return reply.json();
  };
  stage('RESTORE_REVIEW');
  const preview = await review();
  assert.ok(preview.itemCount === 1 && preview.newItemCount === 1 && preview.updatedItemCount === 0 && preview.skippedCount === 0);
  assert.ok(await page.$eval('kn-vault-restore-dialog', node => node.textContent.includes('1 to add; 0 to update; 0 to ignore')));
  assert.equal(await page.$$eval(card, nodes => nodes.length), 0);
  assert.equal(await page.$('output.secret'), null);
  assert.equal(applyRequests, 0);
  await page.locator('#restore-account-password').fill(accountPassword);
  stage('RESTORE_APPLY');
  const applied = await clickResponse('kn-vault-restore-dialog form button[type="submit"]', '/api/v1/vault/restores/apply', 'POST');
  const counts = await applied.json();
  assert.ok(counts.restoredCount === 1 && counts.updatedCount === 0 && counts.skippedCount === 0 && counts.receivingPolicyUnchanged === true);
  await page.waitForSelector('kn-vault-restore-dialog', { hidden: true });
  await expand();
  const restoredId = await itemId();assert.notEqual(restoredId, originalId);
  assert.ok(await page.$eval(card, node => node.textContent.includes('CSP edited item') && node.textContent.includes('csp-user')));
  assert.equal(await page.$$eval(card, nodes => nodes.length), 1);
  await reveal(replacement);
  stage('RESTORE_DUPLICATE');
  const duplicate = await review();
  assert.ok(duplicate.itemCount === 1 && duplicate.newItemCount === 0 && duplicate.updatedItemCount === 0 && duplicate.skippedCount === 1);
  await page.waitForFunction(() => document.querySelector('kn-vault-restore-dialog')?.textContent.includes('All items already exist. Nothing needs to be restored.'));
  assert.equal(await page.$('#restore-account-password'), null);
  assert.equal(await page.$('kn-vault-restore-dialog form button[type="submit"]'), null);
  await page.locator('kn-vault-restore-dialog [mat-dialog-close]').click();
  await page.waitForSelector('kn-vault-restore-dialog', { hidden: true });
  assert.ok(await page.evaluate(() => window.__fixtureViolations.length === 0));
  await page.reload({ waitUntil: 'networkidle0' });
  await expand();assert.equal(await itemId(), restoredId);
  assert.equal(await page.$$eval(card, nodes => nodes.length), 1);
  await reveal(replacement);
  assert.equal(applyRequests, 1);
}
