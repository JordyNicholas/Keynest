"""Fixed diagnostic categories only; never return matching log text."""

import re


def failure_categories(logs):
  # The normal JAVA_TOOL_OPTIONS banner contains HeapDumpOnOutOfMemoryError.
  # Match the exception token, not that disabled-dump option's substring.
  memory = bool(re.search(r'(?:^|\s)(?:java\.lang\.)?OutOfMemoryError(?=\s|:|$)', logs))
  return {
    'native_library_failure': any(marker in logs for marker in ['UnsatisfiedLinkError', 'failed to map segment']),
    'read_only_write': 'Read-only file system' in logs,
    'permission_failure': 'Permission denied' in logs,
    'memory_failure': memory,
    'startup_failure': 'APPLICATION FAILED TO START' in logs,
  }
