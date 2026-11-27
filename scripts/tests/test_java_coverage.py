import contextlib
import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('coverage_gate', Path(__file__).resolve().parents[1] / 'check_java_coverage.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class JavaCoverageTest(unittest.TestCase):
  def setUp(self):
    self.temp = tempfile.TemporaryDirectory()
    self.addCleanup(self.temp.cleanup)
    self.root = Path(self.temp.name)
    (self.root / 'scripts').mkdir()
    (self.root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>libs/one</module><module>apps/two</module></modules></project>')
    self.floors = {module: {'LINE': 0.8, 'BRANCH': 0.8} for module in ['libs/one', 'apps/two']}
    self.write_floors()
    for module in self.floors:
      target = self.root / module / 'target'
      (target / 'classes').mkdir(parents=True)
      (target / 'classes/Example.class').write_bytes(b'synthetic test fixture')
      (target / 'jacoco.exec').write_bytes(b'synthetic test fixture')
      (target / 'site/jacoco').mkdir(parents=True)
      self.write_report(module)

  def write_floors(self):
    (self.root / 'scripts/java-coverage-floors.json').write_text(json.dumps(self.floors))

  def write_report(self, module='libs/one', counters=None):
    counters = counters if counters is not None else '<counter type="LINE" covered="80" missed="20"/><counter type="BRANCH" covered="8" missed="2"/>'
    (self.root / module / 'target/site/jacoco/jacoco.xml').write_text('<report><package name="sample"><class name="sample/Example"/></package>' + counters + '</report>')

  def test_exact_threshold_is_accepted_without_float_rounding(self):
    self.assertEqual(len(gate.check(self.root)), 4)
    self.floors['libs/one']['LINE'] = 0.29
    self.write_floors()
    self.write_report(counters='<counter type="LINE" covered="29" missed="71"/><counter type="BRANCH" covered="8" missed="2"/>')
    self.assertEqual(len(gate.check(self.root)), 4)

  def test_each_module_and_metric_is_enforced_without_averaging(self):
    for module in self.floors:
      for metric in ['LINE', 'BRANCH']:
        with self.subTest(module=module, metric=metric):
          self.write_report(module, ''.join(f'<counter type="{kind}" covered="{79 if kind == metric else 100}" missed="{21 if kind == metric else 0}"/>' for kind in ['LINE', 'BRANCH']))
          with self.assertRaises(ValueError): gate.check(self.root)
          self.write_report(module)

  def test_required_execution_classes_and_report_cannot_be_missing_or_empty(self):
    for suffix in ['jacoco.exec', 'classes/Example.class', 'site/jacoco/jacoco.xml']:
      path = self.root / 'libs/one/target' / suffix
      content = path.read_bytes()
      with self.subTest(suffix=suffix):
        path.unlink()
        with self.assertRaises(ValueError): gate.check(self.root)
        path.write_bytes(content)
    for suffix in ['jacoco.exec', 'site/jacoco/jacoco.xml']:
      path = self.root / 'libs/one/target' / suffix
      content = path.read_bytes()
      path.write_bytes(b'')
      with self.assertRaises(ValueError): gate.check(self.root)
      path.write_bytes(content)

  def test_invalid_zero_missing_or_duplicate_counters_are_refused(self):
    valid_branch = '<counter type="BRANCH" covered="8" missed="2"/>'
    for counter in ['', '<counter type="LINE" covered="0" missed="0"/>', '<counter type="LINE" covered="-1" missed="2"/>', '<counter type="LINE" covered="NaN" missed="2"/>', '<counter type="LINE" covered="1.0" missed="2"/>', '<counter type="LINE" covered="80" missed="20"/>' * 2]:
      with self.subTest(counter=counter):
        self.write_report(counters=counter + valid_branch)
        with self.assertRaises(ValueError): gate.check(self.root)

  def test_floors_cannot_be_disabled_or_nonfinite(self):
    for value in [0, -1, 1.1, True, '0.8', None, float('nan'), float('inf')]:
      with self.subTest(value=value):
        self.floors['libs/one']['LINE'] = value
        self.write_floors()
        with self.assertRaises(ValueError): gate.check(self.root)

  def test_every_reactor_module_and_both_floors_are_required(self):
    original = dict(self.floors)
    for floors in [{}, {'libs/one': original['libs/one']}, {**original, 'extra': original['libs/one']}, {**original, 'libs/one': {'LINE': 0.8}}, []]:
      self.floors = floors
      self.write_floors()
      with self.assertRaises(ValueError): gate.check(self.root)

  def test_malformed_empty_and_oversized_reports_fail_without_echoing_content(self):
    report = self.root / 'libs/one/target/site/jacoco/jacoco.xml'
    for content in ['secret-marker', '<report/>', '<wrong/>', '<report>' + ' ' * (16 * 1024 * 1024) + '</report>']:
      report.write_text(content)
      output, error = io.StringIO(), io.StringIO()
      with contextlib.redirect_stdout(output), contextlib.redirect_stderr(error):
        self.assertEqual(gate.main(self.root), 1)
      self.assertEqual(output.getvalue(), '')
      self.assertTrue(error.getvalue().startswith('FAIL: '))
      self.assertNotIn('secret-marker', error.getvalue())

  def test_invalid_configuration_and_module_paths_fail_closed(self):
    config = self.root / 'scripts/java-coverage-floors.json'
    config.write_text('malformed secret-marker')
    with self.assertRaises(ValueError): gate.check(self.root)
    for module in ['../escape', '/absolute']:
      (self.root / 'pom.xml').write_text(f'<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>{module}</module></modules></project>')
      self.floors = {module: {'LINE': 0.8, 'BRANCH': 0.8}}
      self.write_floors()
      with self.assertRaises(ValueError): gate.check(self.root)


if __name__ == '__main__':
  unittest.main()
