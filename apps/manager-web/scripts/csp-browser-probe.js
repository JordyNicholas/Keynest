/* Loaded only by the isolated build fixture; never packaged in the application. */
(async () => {
  const violations = [];
  let remoteScriptBlocked = false;
  document.addEventListener('securitypolicyviolation', event => {
    violations.push(event.effectiveDirective);
    if (event.effectiveDirective === 'script-src-elem' && event.blockedURI.includes('/_forbidden-script')) remoteScriptBlocked = true;
  });
  const pause = () => new Promise(resolve => setTimeout(resolve, 50));
  const wait = async predicate => {
    for (let i = 0; i < 160; i++) { if (predicate()) return;await pause(); }
    throw new Error('fixture timeout');
  };
  const button = (root, text) => [...root.querySelectorAll('button,a')].find(node => node.textContent.trim() === text);
  const require = value => { if (!value) throw new Error('fixture assertion'); };
  let passed = false;
  let stage = 'BOOT';
  try {
    await wait(() => document.querySelector('#generator mat-expansion-panel-header'));
    stage = 'GENERATOR';
    document.querySelector('#generator mat-expansion-panel-header').click();
    await wait(() => document.querySelector('kn-public-generator button'));
    button(document.querySelector('kn-public-generator'), 'Generate password').click();
    await wait(() => document.querySelector('output.password')?.textContent.length === 20);
    stage = 'CHECKER';
    document.querySelector('#check mat-expansion-panel-header').click();
    await wait(() => document.querySelector('#public-check-sample'));
    const input = document.querySelector('#public-check-sample');
    input.value = 'Synthetic sample!42';input.dispatchEvent(new Event('input', { bubbles: true }));
    button(document.querySelector('kn-public-checker'), 'Show password').click();
    await wait(() => input.type === 'text');
    button(document.querySelector('kn-public-checker'), 'Hide password').click();
    await wait(() => input.type === 'password');
    button(document.querySelector('kn-public-checker'), 'Clear sample').click();
    stage = 'DIALOG';
    button(document, 'Sign in').click();
    await wait(() => document.querySelector('[role="dialog"] input'));
    stage = 'OVERLAY_STYLE';
    require(getComputedStyle(document.querySelector('.cdk-overlay-container')).position === 'fixed');
    stage = 'APPLICATION_CSP';
    require(violations.length === 0);
    stage = 'NEGATIVE_PROBES';
    require((await fetch('/_allowed')).ok);
    const inline = document.createElement('script');inline.textContent = 'window.__cspInjected = true';document.head.append(inline);
    let evalBlocked = false;
    try { new Function('return 1')(); } catch { evalBlocked = true; }
    require(evalBlocked && !window.__cspInjected);
    const remote = document.createElement('script');remote.src = `http://localhost:${location.port}/_forbidden-script`;document.head.append(remote);
    let connectBlocked = false;
    try { await fetch(`http://localhost:${location.port}/_forbidden-fetch`); } catch { connectBlocked = true; }
    await pause();
    require(connectBlocked && remoteScriptBlocked && !window.__cspInjected);
    require(violations.includes('script-src-elem') && violations.includes('script-src') && violations.includes('connect-src'));
    passed = true;
  } catch { /* Only fixed success/failure markers leave this isolated browser. */ }
  finally {
    document.body.replaceChildren(document.createTextNode(passed ? 'KEYNEST_CSP_PASS' : 'KEYNEST_CSP_FAIL_' + stage));
  }
})();
