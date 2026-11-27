"""Run the root Spotless gate only when its fixed adoption commit is available."""

import argparse
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def execute(goal, root=ROOT):
  pom = ET.parse(root / 'pom.xml')
  reference = pom.find("m:profiles/m:profile[m:id='formatting']/m:build/m:plugins/m:plugin/m:configuration/m:ratchetFrom", NS)
  if reference is None or not re.fullmatch(r'[0-9a-f]{40}', reference.text or ''):
    print('FAIL: formatting requires a fixed adoption commit.', file=sys.stderr)
    return 1
  available = subprocess.run(['git', 'cat-file', '-e', reference.text + '^{commit}'], cwd=root,
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
  if available.returncode != 0:
    print('FAIL: formatting adoption commit is not available; fetch the required history.', file=sys.stderr)
    return 1
  executable = shutil.which('mvn') or shutil.which('mvn.cmd')
  if executable is None:
    print('FAIL: Maven executable is unavailable.', file=sys.stderr)
    return 1
  return subprocess.run([executable, '-B', '-N', '-Pformatting', f'spotless:{goal}'],
                        cwd=root, timeout=120).returncode


if __name__ == '__main__':
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('goal', choices=['check', 'apply'])
  args = parser.parse_args()
  try:
    sys.exit(execute(args.goal))
  except (OSError, ET.ParseError, subprocess.TimeoutExpired):
    print('FAIL: formatter configuration or Git execution is unavailable.', file=sys.stderr)
    sys.exit(1)
