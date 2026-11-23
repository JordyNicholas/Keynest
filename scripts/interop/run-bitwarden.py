"""Run only generated synthetic fixtures through an independently pinned upstream importer."""

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

REVISION = '91214c3b978edb5496a32a946451960fe7d44531'
ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'apps/manager-api/target/bitwarden-interop/fixtures.json'


class CheckFailure(Exception):
  pass


def require(condition, message):
  if not condition:
    raise CheckFailure(message)


def run(command, directory, environment, log, stage, timeout=900):
  with log.open('wb') as output:
    result = subprocess.run(command, cwd=directory, env=environment, stdout=output, stderr=subprocess.STDOUT, timeout=timeout, check=False)
  if result.returncode:
    # Compiler/error identifiers are safe diagnostics; never print input values, source excerpts or traces.
    codes = sorted(set(re.findall(r'\b(?:TS\d{4}|E[A-Z][A-Z0-9_]{2,30})\b', log.read_text(errors='replace'))))
    raise CheckFailure(stage + ' failed; diagnostic codes: ' + (','.join(codes[:10]) or 'none (details suppressed)'))


def main():
  require(len(sys.argv) == 2, 'expected a clean pinned upstream checkout directory')
  upstream = Path(sys.argv[1]).resolve()
  revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=upstream, text=True, stderr=subprocess.DEVNULL).strip()
  require(revision == REVISION, 'upstream revision differs from the reviewed pin')
  require(not subprocess.check_output(['git', 'status', '--porcelain'], cwd=upstream, text=True, stderr=subprocess.DEVNULL).strip(), 'upstream checkout must start clean')
  require(FIXTURES.is_file() and 0 < FIXTURES.stat().st_size <= 512 * 1024, 'bounded generated synthetic fixtures are required')
  fixtures = json.loads(FIXTURES.read_text())
  require(fixtures.get('fixture') == 'keynest.synthetic.bitwarden.v1' and len(fixtures.get('batches', [])) == 3, 'unexpected fixture contract')
  spec = upstream / 'libs/importer/src/importers/bitwarden/keynest-interop.spec.ts'
  require(not spec.exists(), 'temporary upstream test path must not exist')
  environment = dict(os.environ, CI='true', HUSKY='0', ELECTRON_SKIP_BINARY_DOWNLOAD='1', PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1',
    KEYNEST_BITWARDEN_FIXTURE=str(FIXTURES), NODE_OPTIONS='--max-old-space-size=4096')
  with tempfile.TemporaryDirectory(prefix='keynest-bitwarden-interop-') as temporary:
    temporary = Path(temporary)
    print('Checking pinned upstream dependencies (install scripts disabled).', flush=True)
    run(['npm', 'ci', '--ignore-scripts', '--no-audit', '--no-fund'], upstream, environment, temporary / 'install.log', 'upstream dependency installation')
    run(['git', 'diff', '--exit-code'], upstream, environment, temporary / 'source.log', 'unchanged upstream source check')
    shutil.copyfile(ROOT / 'scripts/interop/bitwarden-importer.spec.ts', spec)
    try:
      report = temporary / 'result.json'
      print('Checking synthetic candidate with real upstream importer/models; no transformation mocks.', flush=True)
      run(['node', 'node_modules/jest/bin/jest.js', '--config', 'libs/importer/jest.config.js', '--runInBand', '--runTestsByPath',
        str(spec), '--silent', '--json', '--outputFile', str(report)], upstream, environment, temporary / 'test.log', 'upstream importer verification')
      result = json.loads(report.read_text())
      require(result.get('success') is True and result.get('numPassedTests') == 4 and result.get('numPendingTests') == 0, 'all four importer checks must execute and pass')
      print('PASS: Bitwarden cli-v2026.10.1 importer at ' + REVISION + '; 4 checks, 3 batches, 511 synthetic logins; no cloud account or destination persistence.')
    finally:
      spec.unlink(missing_ok=True)


if __name__ == '__main__':
  try:
    main()
  except CheckFailure as error:
    print('FAIL: ' + str(error), file=sys.stderr)
    sys.exit(1)
  except Exception:
    print('FAIL: interoperability setup/runtime error (details suppressed)', file=sys.stderr)
    sys.exit(1)
