"""Exercise real Spotless failures/repair in an owned, disposable detached worktree."""

import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE = 'c609e9634b731afc0abc4f1b94a09de8f46a1207'


def run(command, cwd):
  return subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        text=True, timeout=180)


def maven(worktree, goal):
  return run(['mvn', '-B', '-N', '-Pformatting', f'spotless:{goal}'], worktree)


def require(condition, category):
  if not condition:
    raise RuntimeError(category)


def probe(worktree, relative, package):
  path = worktree / relative
  original = path.read_bytes() if path.exists() else None
  try:
    path.parent.mkdir(parents=True, exist_ok=True)
    if package:
      bad = f'package {package};\npublic class FormattingProbe{{public int value(){{return 1;}}}}\n'
    else:
      require(original is not None, 'legacy probe target is missing')
      bad = '   ' + original.decode('utf-8')
    path.write_text(bad, encoding='utf-8')
    require(run(['git', 'add', '--', relative], worktree).returncode == 0, 'could not stage probe')
    rejected = maven(worktree, 'check')
    require(rejected.returncode != 0 and 'format violations' in rejected.stdout.lower(),
            f'{relative}: expected a real formatting rejection')
    require(maven(worktree, 'apply').returncode == 0, f'{relative}: repair failed')
    fixed = path.read_text(encoding='utf-8')
    require(fixed != bad and '\t' not in fixed, f'{relative}: repair did not normalize the probe')
    if package:
      require('  public int value() {' in fixed, f'{relative}: expected two-space method indentation')
    require(maven(worktree, 'check').returncode == 0, f'{relative}: repaired source was rejected')
  finally:
    if original is None:
      path.unlink(missing_ok=True)
    else:
      path.write_bytes(original)
    require(run(['git', 'reset', '--quiet', 'HEAD', '--', relative], worktree).returncode == 0,
            'could not clear private probe index')


def verify():
  with tempfile.TemporaryDirectory(prefix='keynest-formatting-') as temporary:
    worktree = Path(temporary) / 'probe'
    require(run(['git', 'worktree', 'add', '--quiet', '--detach', str(worktree), 'HEAD'], ROOT).returncode == 0,
            'could not create disposable worktree')
    try:
      require(maven(worktree, 'check').returncode == 0, 'committed candidate did not pass before probes')
      targets = [
        ('libs/keynest-policy/src/main/java/dev/keynest/policy/FormattingProbe.java', 'dev.keynest.policy'),
        ('apps/manager-api/src/test/java/dev/keynest/formatting/FormattingProbe.java', 'dev.keynest.formatting'),
        ('examples/external-consumer/src/main/java/dev/keynest/examples/FormattingProbe.java', 'dev.keynest.examples'),
        ('examples/external-consumer/src/test/java/dev/keynest/examples/FormattingProbe.java', 'dev.keynest.examples'),
        ('libs/keynest-policy/src/main/java/dev/keynest/policy/PolicyOperation.java', None),
      ]
      for relative, package in targets:
        probe(worktree, relative, package)
        print(f'PASS: rejected, repaired and rechecked {relative}')
      pom = worktree / 'pom.xml'
      original = pom.read_text(encoding='utf-8')
      marker = f'<ratchetFrom>{BASELINE}</ratchetFrom>'
      require(original.count(marker) == 1, 'fixed adoption baseline was not found')
      try:
        pom.write_text(original.replace(marker, '<ratchetFrom>' + 'f' * 40 + '</ratchetFrom>'), encoding='utf-8')
        rejected = maven(worktree, 'check')
        require(rejected.returncode != 0 and any(term in rejected.stdout.lower() for term in ['reference', 'ratchetfrom']),
                'missing adoption reference did not fail closed')
      finally:
        pom.write_text(original, encoding='utf-8')
      require(maven(worktree, 'check').returncode == 0, 'candidate did not recover after restoring baseline')
      print('PASS: missing adoption reference refused; restored reference passed')
    finally:
      require(run(['git', 'worktree', 'remove', '--force', str(worktree)], ROOT).returncode == 0,
              'disposable worktree cleanup failed')


if __name__ == '__main__':
  try:
    verify()
  except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
    # Maven output can contain source diffs; report only our fixed failure category.
    category = str(error) if isinstance(error, RuntimeError) else type(error).__name__
    print(f'FAIL: formatter verification: {category}', file=sys.stderr)
    sys.exit(1)
