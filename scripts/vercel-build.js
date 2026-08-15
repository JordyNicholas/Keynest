const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const web = path.join(root, 'apps/manager-web');
const browserDir = path.join(web, 'dist/manager-web/browser');
const outDir = path.join(root, 'out');

function run(command, cwd = root) {
  console.log(`$ ${command}`);
  execSync(command, { cwd, stdio: 'inherit', env: process.env });
}

run('npm ci', web);
run('npm run build', web);

if (!fs.existsSync(path.join(browserDir, 'index.html'))) {
  console.error('Angular build missing index.html at', browserDir);
  process.exit(1);
}

fs.rmSync(outDir, { recursive: true, force: true });
fs.cpSync(browserDir, outDir, { recursive: true });
console.log('Copied Angular browser build to /out for Vercel outputDirectory');

run('node scripts/prepare-vercel-output.js', root);
