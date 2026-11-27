"""Fail on incomplete SpotBugs output, new findings or stale source-bound review entries."""

import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
REASONS = {
  'startup-validation', 'sanitized-failure-boundary', 'one-time-csprng-process-key',
  'shared-admission-monitor', 'mutable-policy-document', 'operator-configuration-binding',
  'managed-ciphertext-entity', 'unmodifiable-stream-selection', 'request-policy-transport',
  'owner-metadata-transport', 'restore-ticket-transport', 'managed-dependency',
}


class AnalysisFailure(ValueError):
  pass


def require(condition, category):
  if not condition:
    raise AnalysisFailure(category)


def module_paths(root):
  modules = [m.text for m in ET.parse(root / 'pom.xml').findall('m:modules/m:module', NS)]
  require(modules and len(modules) == len(set(modules)), 'empty or duplicate reactor inventory')
  for module in modules:
    require(isinstance(module, str) and re.fullmatch(r'[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*', module), 'invalid reactor module')
    require((root / module).resolve().is_relative_to(root.resolve()), 'module escapes repository')
  return modules


def source_digest(root, module, owner):
  require(re.fullmatch(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*', owner), 'invalid reviewed class')
  source = root / module / 'src/main/java' / (owner.split('$')[0].replace('.', '/') + '.java')
  require(source.resolve().is_relative_to((root / module).resolve()), 'source escapes module')
  return hashlib.sha256(source.read_text(encoding='utf-8').encode('utf-8')).hexdigest()


def reviewed(root, modules):
  rows = json.loads((root / 'config/spotbugs-reviewed.json').read_text())
  require(isinstance(rows, list), 'invalid review inventory')
  expected = {module: Counter() for module in modules}
  seen = set()
  for row in rows:
    require(isinstance(row, dict) and set(row) == {'module', 'class', 'source_sha256', 'findings'}, 'invalid review group')
    module, owner = row['module'], row['class']
    require(module in expected and isinstance(owner, str) and (module, owner) not in seen, 'unknown or duplicate reviewed class')
    seen.add((module, owner))
    require(isinstance(row['source_sha256'], str) and re.fullmatch(r'[0-9a-f]{64}', row['source_sha256']), 'invalid review digest')
    require(source_digest(root, module, owner) == row['source_sha256'], f'{module}: reviewed source changed; re-review required')
    require(isinstance(row['findings'], list) and row['findings'], 'empty review group')
    for finding in row['findings']:
      require(isinstance(finding, dict) and set(finding) == {'type', 'priority', 'method', 'field', 'count', 'reason'}, 'invalid reviewed finding')
      require(finding['reason'] in REASONS, 'unknown review rationale')
      key = fingerprint(owner, finding['type'], finding['priority'], finding['method'], finding['field'])
      require(key not in expected[module], 'duplicate reviewed fingerprint')
      count = finding['count']
      require(type(count) is int and 0 < count <= 100, 'invalid reviewed count')
      expected[module][key] = count
  return expected


def fingerprint(owner, kind, priority, method, field):
  require(isinstance(owner, str) and re.fullmatch(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*', owner), 'invalid finding class')
  require(isinstance(kind, str) and re.fullmatch(r'[A-Z][A-Z0-9_]+', kind), 'invalid finding type')
  require(type(priority) is int and priority in (1, 2), 'unexpected finding priority')
  require(isinstance(method, str) and (method in ('', '<init>', '<clinit>') or re.fullmatch(r'[A-Za-z_$][\w$]*', method)), 'invalid finding method')
  require(isinstance(field, str) and (not field or re.fullmatch(r'[A-Za-z_$][\w$]*', field)), 'invalid finding field')
  return owner, kind, priority, method, field


def inspect_module(root, module, expected):
  target = root / module / 'target'
  require(any((target / 'classes').rglob('*.class')), f'{module}: missing production classes')
  report = target / 'spotbugsXml.xml'
  require(report.is_file() and 0 < report.stat().st_size <= 32 * 1024 * 1024, f'{module}: missing or oversized analyzer report')
  document = ET.parse(report).getroot()
  require(document.tag == 'BugCollection' and document.get('version') == '4.10.5', f'{module}: unexpected analyzer report')
  summary, errors = document.find('FindBugsSummary'), document.find('Errors')
  require(summary is not None and errors is not None, f'{module}: incomplete analyzer report')
  classes = int(summary.get('total_classes', '-1'))
  bugs = document.findall('BugInstance')
  require(classes > 0 and int(summary.get('total_bugs', '-1')) == len(bugs), f'{module}: incomplete class/finding counts')
  require(errors.get('errors') == '0' and errors.get('missingClasses') == '0' and not list(errors), f'{module}: analyzer errors or missing classes')
  observed = Counter()
  for bug in bugs:
    owner, method, field = bug.find('Class'), bug.find('Method'), bug.find('Field')
    require(owner is not None, f'{module}: missing finding owner')
    observed[fingerprint(owner.get('classname'), bug.get('type'), int(bug.get('priority', '-1')),
                         method.get('name') if method is not None else '', field.get('name') if field is not None else '')] += 1
  require(observed == expected, f'{module}: new, changed or stale reviewed findings')
  return f'{module}: {classes} classes, {sum(observed.values())} source-reviewed findings, zero unreviewed findings'


def check(root):
  modules = module_paths(root)
  expected = reviewed(root, modules)
  return [inspect_module(root, module, expected[module]) for module in modules]


def main(root=ROOT):
  try:
    summaries = check(root)
  except AnalysisFailure as error:
    print(f'FAIL: {error}', file=sys.stderr)
    return 1
  except (OSError, ValueError, TypeError, KeyError, ET.ParseError):
    print('FAIL: analyzer report or review metadata is invalid; payloads suppressed.', file=sys.stderr)
    return 1
  print('\n'.join(summaries))
  return 0


if __name__ == '__main__':
  sys.exit(main())
