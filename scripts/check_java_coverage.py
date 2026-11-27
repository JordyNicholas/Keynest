"""Enforce per-module JaCoCo XML floors and required build outputs; no external service."""

import json
import re
import sys
import xml.etree.ElementTree as ET
from decimal import Decimal, InvalidOperation
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METRICS = {'LINE', 'BRANCH'}
NAMESPACE = {'m': 'http://maven.apache.org/POM/4.0.0'}


class CoverageFailure(ValueError):
  """A controlled message containing only source module/metric names."""


def check(root):
  modules = [element.text for element in ET.parse(root / 'pom.xml').findall('m:modules/m:module', NAMESPACE)]
  floors = json.loads((root / 'scripts/java-coverage-floors.json').read_text(), parse_float=Decimal)
  if not modules or len(modules) != len(set(modules)) or not isinstance(floors, dict) or set(floors) != set(modules):
    raise CoverageFailure('coverage floors must match every direct reactor module')
  summaries = []
  for module in modules:
    if not isinstance(module, str) or not re.fullmatch(r'[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*', module):
      raise CoverageFailure('invalid module path')
    directory = (root / module).resolve()
    if not directory.is_relative_to(root.resolve()):
      raise CoverageFailure('module escapes repository')
    limits = floors[module]
    if not isinstance(limits, dict) or set(limits) != METRICS:
      raise CoverageFailure(f'{module}: LINE and BRANCH floors are required')
    for minimum in limits.values():
      if isinstance(minimum, bool) or not isinstance(minimum, (int, Decimal)) or not Decimal(minimum).is_finite() or not 0 < minimum <= 1:
        raise CoverageFailure(f'{module}: floors must be finite and greater than zero, at most one')
    target = directory / 'target'
    execution = target / 'jacoco.exec'
    if not execution.is_file() or execution.stat().st_size == 0:
      raise CoverageFailure(f'{module}: missing execution data')
    if not any((target / 'classes').rglob('*.class')):
      raise CoverageFailure(f'{module}: missing production classes')
    report = target / 'site/jacoco/jacoco.xml'
    if not report.is_file() or not 0 < report.stat().st_size <= 16 * 1024 * 1024:
      raise CoverageFailure(f'{module}: missing or oversized XML report')
    document = ET.parse(report).getroot()
    if document.tag != 'report' or not document.findall('package/class'):
      raise CoverageFailure(f'{module}: empty or invalid coverage report')
    counters = {}
    for counter in document.findall('counter'):
      kind = counter.get('type')
      if kind in METRICS:
        if kind in counters:
          raise CoverageFailure(f'{module}: duplicate coverage counter')
        values = [counter.get('covered', ''), counter.get('missed', '')]
        if not all(re.fullmatch(r'[0-9]+', value) for value in values):
          raise CoverageFailure(f'{module}: invalid coverage counts')
        covered, missed = map(int, values)
        total = covered + missed
        if total == 0:
          raise CoverageFailure(f'{module}: empty coverage counter')
        counters[kind] = (covered, total)
    if set(counters) != METRICS:
      raise CoverageFailure(f'{module}: missing coverage counters')
    for kind in sorted(METRICS):
      covered, total = counters[kind]
      minimum = Decimal(limits[kind])
      if Decimal(covered) < minimum * total:
        raise CoverageFailure(f'{module}: {kind} coverage is below its floor')
      summaries.append(f'{module} {kind}: {covered}/{total} ({100 * Decimal(covered) / total:.2f}%), floor {100 * minimum}%')
  return summaries


def main(root=ROOT):
  try:
    summaries = check(root)
  except CoverageFailure as error:
    print(f'FAIL: {error}', file=sys.stderr)
    return 1
  except (OSError, ValueError, ET.ParseError, InvalidOperation):
    # Fixed message: malformed report/configuration content must not become diagnostic output.
    print('FAIL: Java coverage floor or required report/data validation failed.', file=sys.stderr)
    return 1
  print('\n'.join(summaries))
  return 0


if __name__ == '__main__':
  sys.exit(main())
