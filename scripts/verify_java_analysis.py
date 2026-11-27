"""Prove analyzer and gate behavior with a real null dereference in an owned worktree."""

import os
import signal
import subprocess
import sys
import tempfile
from pathlib import Path

import check_java_analysis as gate

ROOT = Path(__file__).resolve().parents[1]
MODULE = 'libs/keynest-policy'
OWNER = 'dev.keynest.policy.StaticAnalysisProbe'


def run(command, cwd, timeout=240):
  process = subprocess.Popen(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             text=True, start_new_session=os.name != 'nt')
  try:
    output, _ = process.communicate(timeout=timeout)
    return subprocess.CompletedProcess(command, process.returncode, output)
  except subprocess.TimeoutExpired:
    if os.name == 'nt':
      subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
    else:
      try:
        os.killpg(process.pid, signal.SIGKILL)
      except ProcessLookupError:
        pass
    process.communicate(timeout=15)
    raise


def require(condition, category):
  if not condition:
    raise RuntimeError(category)


def analyze(worktree):
  # Compile the disposable fixture; the normal candidate's full test run is separate and retained.
  result = run(['mvn', '-B', '-Pstatic-analysis', '-pl', MODULE, '-am',
                'clean', 'compile', 'spotbugs:spotbugs'], worktree)
  if result.returncode != 0:
    if '429' in result.stdout and 'Too Many Requests' in result.stdout:
      raise RuntimeError('disposable dependency service throttled')
    if 'Could not transfer artifact' in result.stdout or 'Could not resolve dependencies' in result.stdout:
      raise RuntimeError('disposable dependency resolution failed')
    raise RuntimeError('disposable compile/analyzer execution failed')


def verify():
  with tempfile.TemporaryDirectory(prefix='keynest-analysis-') as temporary:
    worktree = Path(temporary) / 'probe'
    require(run(['git', 'worktree', 'add', '--quiet', '--detach', str(worktree), 'HEAD'], ROOT).returncode == 0,
            'could not create disposable analysis worktree')
    try:
      expected = gate.reviewed(worktree, gate.module_paths(worktree))[MODULE]
      source = worktree / MODULE / 'src/main/java/dev/keynest/policy/StaticAnalysisProbe.java'
      require(not source.exists(), 'disposable probe would overwrite an existing source')
      source.write_text('package dev.keynest.policy; public final class StaticAnalysisProbe {\n'
                        '  public int value() { Object value = null; return value.hashCode(); }\n}\n')
      analyze(worktree)
      import xml.etree.ElementTree as ET
      report = ET.parse(worktree / MODULE / 'target/spotbugsXml.xml').getroot()
      detected = any(b.get('type', '').startswith('NP_') and b.find('Class') is not None
                     and b.find('Class').get('classname') == OWNER for b in report.findall('BugInstance'))
      require(detected, 'real null-dereference fixture was not detected')
      try:
        gate.inspect_module(worktree, MODULE, expected)
      except gate.AnalysisFailure as failure:
        require('new, changed or stale reviewed findings' in str(failure), 'probe failed for an unrelated report error')
      else:
        raise RuntimeError('real unreviewed defect was accepted')
      print('PASS: real null dereference detected and rejected')
      source.write_text('package dev.keynest.policy; public final class StaticAnalysisProbe {\n'
                        '  public int value() { return "ok".length(); }\n}\n')
      analyze(worktree)
      gate.inspect_module(worktree, MODULE, expected)
      print('PASS: repaired control recompiled, reanalyzed and accepted')
      report_path = worktree / MODULE / 'target/spotbugsXml.xml'
      report_path.unlink()
      try:
        gate.inspect_module(worktree, MODULE, expected)
      except gate.AnalysisFailure as failure:
        require('missing or oversized analyzer report' in str(failure), 'missing report failed for an unrelated reason')
      else:
        raise RuntimeError('missing report was accepted')
      print('PASS: missing analyzer report refused')
    finally:
      require(run(['git', 'worktree', 'remove', '--force', str(worktree)], ROOT, 30).returncode == 0,
              'disposable analysis worktree cleanup failed')


if __name__ == '__main__':
  try:
    verify()
  except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as failure:
    # RuntimeError categories above are fixed; never print arbitrary analyzer/parser payloads.
    category = str(failure) if type(failure) is RuntimeError else type(failure).__name__
    print(f'FAIL: real analyzer verification: {category}; payloads suppressed.', file=sys.stderr)
    sys.exit(1)
