# Web CSP production-build compatibility fixture

Part of H1 / #278, tracked by #282. This fixture is a compatibility gate; it does not install production response headers or amend session transport.

`npm run test:build-config` in `apps/manager-web` retains the custom-API and same-origin build checks and starts a disposable headless Chromium profile for each actual production artifact. Set `CHROME_BIN` to the installed Chromium/Chrome executable when it is not `google-chrome`. The loopback HTTP fixture serves the output with the candidate policy, injects one same-origin test driver outside the packaged app, and deletes the profile after use. Node syntax can be checked without npm dependencies; actual build/browser success requires installed dependencies and the browser.

The candidate restricts scripts to self without inline/eval exemptions; connections to self and the build-validated API origin; object/framing/form/base capabilities explicitly. It permits inline styles for Angular/Material and the existing narrow Google Fonts style/font origins. This style exception and third-party font egress are explicit review limitations, not a nonce-based style guarantee. Unexpected CSP violations from application startup and exercised UI fail the fixture before deliberate negative probes.

The browser opens the homepage, generates a transient password, switches to the checker, toggles visibility/clears the synthetic sample, and opens the Material sign-in dialog with an overlay style check. Negative probes attempt an inline script, eval, unlisted script and unlisted fetch; the latter two must not reach the server. Only fixed outcome markers are returned. Browser DOM, generated values and diagnostic payloads must not be printed, including failure/timeout paths.

This does not verify authenticated Vault/calendar/export/restore journeys, actual requests to a separately hosted API, frame embedding, real Vercel/proxy headers, all browsers, or performance/font rendering. Those remain H1 acceptance work before enforcement. No public host is probed. Candidate fixture success alone does not close H1 or resolve T02.

Rollback removes the fixture and calls in the build verifier; the pre-existing production bundle checks must remain. No vault data/schema/key/session or API change is involved. Any subsequent builder optimization adjustment must document its rendering/performance impact separately.
