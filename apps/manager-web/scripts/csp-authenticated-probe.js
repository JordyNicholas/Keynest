/* CI-only driver: synthetic account and data in the disposable Compose database. */
(async () => {
  let stage = 'BOOT';
  let passed = false;
  const violations = [];
  document.addEventListener('securitypolicyviolation', event => violations.push(event.effectiveDirective));
  const pause = () => new Promise(resolve => setTimeout(resolve, 50));
  const wait = async predicate => {
    for (let i = 0; i < 240; i++) { if (predicate()) return;await pause(); }
    throw new Error('fixture timeout');
  };
  const require = value => { if (!value) throw new Error('fixture assertion'); };
  const button = (root, text) => [...root.querySelectorAll('button,a')].find(node => node.textContent.trim() === text);
  const fill = (selector, value) => {
    const input = document.querySelector(selector);require(input);input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  };
  try {
    await wait(() => document.querySelector('#home-heading'));
    stage = 'SETUP_ACCOUNT';
    const email = `csp-${crypto.randomUUID()}@example.invalid`;
    const password = 'Synthetic account password!42';
    const registered = await fetch('/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }) });
    require(registered.ok);
    stage = 'SIGN_IN';
    button(document, 'Sign in').click();
    await wait(() => document.querySelector('#auth-email'));
    await wait(() => document.querySelector('[role="dialog"]').getAnimations({ subtree: true })
      .every(animation => animation.playState !== 'running'));
    await pause();
    stage = 'SIGN_IN_FIELDS';
    fill('#auth-email', email);fill('#auth-password', password);
    await pause();
    require(document.querySelector('#auth-email').value === email && document.querySelector('#auth-password').value === password);
    stage = 'SIGN_IN_SUBMIT';
    const form = document.querySelector('[role="dialog"] form');
    require(form.checkValidity());
    form.requestSubmit(button(document.querySelector('[role="dialog"]'), 'Sign in'));
    stage = 'WAIT_SESSION';
    await wait(() => document.querySelector('.shell.authenticated') && !document.querySelector('[role="dialog"]'));
    stage = 'SESSION_STORAGE';
    require(sessionStorage.getItem('keynest.token'));
    stage = 'VAULT_CREATE';
    document.querySelector('nav a[href="/vault"]').click();
    await wait(() => document.querySelector('a[href="/vault/new"]'));
    document.querySelector('a[href="/vault/new"]').click();
    await wait(() => document.querySelector('input[name="label"]'));
    fill('input[name="label"]', 'CSP synthetic item');
    const syntheticSecret = 'Q7!mZ3#rT8$vL2@pX9%w';
    fill('input[type="password"]', syntheticSecret);
    stage = 'CALENDAR';
    document.querySelector('mat-datepicker-toggle button').click();
    await wait(() => document.querySelector('mat-datepicker-content'));
    require(document.querySelector('mat-datepicker-content').getBoundingClientRect().height > 0);
    document.querySelector('.cdk-overlay-backdrop').click();
    await wait(() => !document.querySelector('mat-datepicker-content'));
    stage = 'SAVE';
    await pause();button(document, 'Save item').click();
    await wait(() => [...document.querySelectorAll('mat-expansion-panel-header strong')].some(n => n.textContent === 'CSP synthetic item'));
    stage = 'REVEAL';
    document.querySelector('.vault-card mat-expansion-panel-header').click();
    await wait(() => document.querySelector('.reveal-action'));
    document.querySelector('.reveal-action').click();
    await wait(() => document.querySelector('output.secret')?.textContent === syntheticSecret);
    stage = 'CONCEAL';
    document.querySelector('.reveal-action').click();
    await wait(() => !document.querySelector('output.secret'));
    stage = 'LOGOUT';
    button(document, 'Sign out').click();
    await wait(() => document.querySelector('#home-heading') && !sessionStorage.getItem('keynest.token'));
    require(!document.querySelector('.shell.authenticated'));
    stage = 'CSP';require(violations.length === 0);
    passed = true;
  } catch {
    // Classify only known public UI messages/directives, never echo DOM or input values.
    if (stage === 'WAIT_SESSION') {
      if (document.body.textContent.includes('Enter your Keynest password.')) stage = 'PASSWORD_REQUIRED';
      else if (document.body.textContent.includes('Enter a valid email address, such as name@example.com.')) stage = 'EMAIL_REQUIRED';
      else if (violations.includes('connect-src')) stage = 'CSP_CONNECT';
    }
  }
  finally {
    sessionStorage.clear();localStorage.clear();
    document.body.replaceChildren(document.createTextNode(passed ? 'KEYNEST_CSP_PASS' : 'KEYNEST_CSP_FAIL_' + stage));
  }
})();
