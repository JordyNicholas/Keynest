#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');

async function main() {
  const root = path.resolve(__dirname, '..');
  const browserDir = path.join(root, 'apps/manager-web/dist/manager-web/browser');
  const outputRoot = path.join(root, '.vercel/output');
  const staticDir = path.join(outputRoot, 'static');

  if (!fs.existsSync(path.join(browserDir, 'index.html'))) {
    console.error('Angular browser build missing at', browserDir);
    process.exit(1);
  }

  const { validatePolicy } = await import(pathToFileURL(path.join(root, 'apps/manager-web/scripts/web-security.mjs')).href);
  const policy = validatePolicy(JSON.parse(fs.readFileSync(path.join(browserDir, 'web-security.json'), 'utf8')));

  fs.rmSync(outputRoot, { recursive: true, force: true });
  fs.mkdirSync(staticDir, { recursive: true });
  fs.cpSync(browserDir, staticDir, { recursive: true });

  const config = {
    version: 3,
    routes: [
      { src: '/(.*)', headers: policy.responseHeaders, continue: true },
      { handle: 'filesystem' },
      { src: '/(.*)', dest: '/index.html' },
    ],
  };

  fs.writeFileSync(path.join(outputRoot, 'config.json'), JSON.stringify(config, null, 2));
  console.log('Prepared Vercel Build Output API from', browserDir);

}
main().catch(() => {
  console.error('Unable to prepare validated Web security output');
  process.exitCode = 1;
});
