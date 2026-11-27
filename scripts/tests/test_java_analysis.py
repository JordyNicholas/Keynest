import contextlib
import hashlib
import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('analysis_gate', Path(__file__).resolve().parents[1] / 'check_java_analysis.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class JavaAnalysisTest(unittest.TestCase):
  def setUp(self):
    self.temp = tempfile.TemporaryDirectory()
    self.addCleanup(self.temp.cleanup)
    self.root = Path(self.temp.name)
    (self.root / 'config').mkdir()
    (self.root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>libs/sample</module></modules></project>')
    self.source = self.root / 'libs/sample/src/main/java/sample/Bean.java'
    self.source.parent.mkdir(parents=True)
    self.source.write_text('package sample; public class Bean {}\n')
    self.target = self.root / 'libs/sample/target'
    (self.target / 'classes').mkdir(parents=True)
    (self.target / 'classes/Bean.class').write_bytes(b'synthetic test fixture')
    self.rows = []
    self.write_rows()
    self.write_report()

  def write_rows(self):
    (self.root / 'config/spotbugs-reviewed.json').write_text(json.dumps(self.rows))

  def write_report(self, bugs='', errors='<Errors errors="0" missingClasses="0"/>', count=None, classes=1):
    count = bugs.count('<BugInstance') if count is None else count
    self.report = self.target / 'spotbugsXml.xml'
    self.report.write_text(f'<BugCollection version="4.10.5"><FindBugsSummary total_classes="{classes}" total_bugs="{count}"/>{errors}{bugs}</BugCollection>')

  def bug(self, kind='EI_EXPOSE_REP', field='values'):
    return f'<BugInstance type="{kind}" priority="2"><Class classname="sample.Bean"/><Method name="values"/><Field name="{field}"/></BugInstance>'

  def review(self, count=1):
    self.rows = [{'module': 'libs/sample', 'class': 'sample.Bean',
                  'source_sha256': hashlib.sha256(self.source.read_text().encode()).hexdigest(),
                  'findings': [{'type': 'EI_EXPOSE_REP', 'priority': 2, 'method': 'values', 'field': 'values',
                                'count': count, 'reason': 'mutable-policy-document'}]}]
    self.write_rows()

  def test_clean_and_exact_source_review_pass(self):
    self.assertEqual(len(gate.check(self.root)), 1)
    self.review()
    self.write_report(self.bug())
    self.assertIn('1 source-reviewed', gate.check(self.root)[0])

  def test_new_changed_extra_and_stale_findings_fail(self):
    self.review()
    for bugs in ['', self.bug('NP_ALWAYS_NULL'), self.bug(field='other'), self.bug() * 2, self.bug() + self.bug('NP_ALWAYS_NULL')]:
      with self.subTest(bugs=bugs):
        self.write_report(bugs)
        with self.assertRaises(ValueError): gate.check(self.root)

  def test_any_reviewed_source_edit_requires_review(self):
    self.review()
    self.write_report(self.bug())
    self.source.write_text('package sample; public class Bean { int changed; }\n')
    with self.assertRaises(ValueError): gate.check(self.root)

  def test_missing_empty_and_incomplete_data_fail(self):
    self.report.unlink()
    with self.assertRaises(ValueError): gate.check(self.root)
    self.report.write_text('')
    with self.assertRaises(ValueError): gate.check(self.root)
    self.write_report(classes=0)
    with self.assertRaises(ValueError): gate.check(self.root)
    self.write_report(count=1)
    with self.assertRaises(ValueError): gate.check(self.root)
    self.write_report()
    (self.target / 'classes/Bean.class').unlink()
    with self.assertRaises(ValueError): gate.check(self.root)

  def test_analyzer_errors_and_missing_dependencies_fail(self):
    for errors in ['', '<Errors errors="1" missingClasses="0"/>', '<Errors errors="0" missingClasses="1"/>', '<Errors errors="0" missingClasses="0"><MissingClass>sample.Missing</MissingClass></Errors>']:
      self.write_report(errors=errors)
      with self.assertRaises(ValueError): gate.check(self.root)

  def test_broad_duplicate_or_invalid_review_entries_fail(self):
    self.review()
    self.write_report(self.bug())
    for key, value in [('class', 'sample.*'), ('module', '../escape'), ('source_sha256', '0' * 64)]:
      original = self.rows[0][key]
      self.rows[0][key] = value
      self.write_rows()
      with self.assertRaises(ValueError): gate.check(self.root)
      self.rows[0][key] = original
    self.rows.append(self.rows[0])
    self.write_rows()
    with self.assertRaises(ValueError): gate.check(self.root)

  def test_unreported_new_reactor_module_fails(self):
    pom = self.root / 'pom.xml'
    pom.write_text(pom.read_text().replace('</modules>', '<module>libs/missing</module></modules>'))
    with self.assertRaises(ValueError): gate.check(self.root)

  def test_malformed_payload_is_not_printed(self):
    self.report.write_text('secret-marker')
    out, error = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(error):
      self.assertEqual(gate.main(self.root), 1)
    self.assertEqual(out.getvalue(), '')
    self.assertNotIn('secret-marker', error.getvalue())


if __name__ == '__main__':
  unittest.main()
