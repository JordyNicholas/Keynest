import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('diagnostics', Path(__file__).resolve().parents[1] / 'container_diagnostics.py')
diagnostics = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diagnostics)


class ContainerDiagnosticsTest(unittest.TestCase):
  def test_normal_disabled_dump_banner_is_not_memory_exhaustion(self):
    banner = ('Picked up JAVA_TOOL_OPTIONS: -XX:-HeapDumpOnOutOfMemoryError '
              '-XX:-CreateCoredumpOnCrash -XX:+DisableAttachMechanism -XX:ErrorFile=/dev/null')
    self.assertFalse(any(diagnostics.failure_categories(banner).values()))

  def test_actual_exception_signatures_are_detected_with_the_banner(self):
    for message in ['java.lang.OutOfMemoryError: Java heap space',
                    'Caused by: java.lang.OutOfMemoryError: unable to create native thread',
                    'Exception in thread "main" java.lang.OutOfMemoryError: Direct buffer memory',
                    'OutOfMemoryError']:
      with self.subTest(message=message):
        self.assertTrue(diagnostics.failure_categories('-XX:-HeapDumpOnOutOfMemoryError\n' + message)['memory_failure'])

  def test_other_failure_categories_remain_independent(self):
    markers = {'native_library_failure': 'UnsatisfiedLinkError',
               'read_only_write': 'Read-only file system',
               'permission_failure': 'Permission denied',
               'startup_failure': 'APPLICATION FAILED TO START'}
    for category, marker in markers.items():
      result = diagnostics.failure_categories(marker)
      self.assertTrue(result[category])
      self.assertFalse(result['memory_failure'])

  def test_result_is_fixed_booleans_without_log_content(self):
    result = diagnostics.failure_categories('synthetic-sensitive-sentinel java.lang.OutOfMemoryError: synthetic-details')
    self.assertEqual(set(result), {'native_library_failure', 'read_only_write', 'permission_failure', 'memory_failure', 'startup_failure'})
    self.assertTrue(all(type(value) is bool for value in result.values()))
    self.assertNotIn('synthetic', repr(result))


if __name__ == '__main__':
  unittest.main()
