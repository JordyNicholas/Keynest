"""Check actual local Compose security controls without printing container configuration."""

import json
import re
import subprocess
import sys


def output(*command):
  result = subprocess.run(command, capture_output=True, text=True, check=False)
  if result.returncode != 0:
    raise RuntimeError('container verification command failed')
  return result.stdout.strip()


def check(condition, name):
  if not condition:
    raise RuntimeError(name)


try:
  container = output('docker', 'compose', '-f', 'deploy/docker-compose.yml', 'ps', '-q', 'manager-api')
  check(bool(re.fullmatch(r'[a-f0-9]{12,64}', container)), 'expected one running API container')
  if '--diagnostics' in sys.argv:
    state = json.loads(output('docker', 'inspect', '--format', '{{json .State}}', container))
    logs = subprocess.run(['docker', 'logs', container], capture_output=True, text=True, check=False)
    combined = logs.stdout + logs.stderr
    categories = {
      'native_library_failure': ['UnsatisfiedLinkError', 'failed to map segment'],
      'read_only_write': ['Read-only file system'],
      'permission_failure': ['Permission denied'],
      'memory_failure': ['OutOfMemoryError'],
      'startup_failure': ['APPLICATION FAILED TO START'],
    }
    print('Container running:', state.get('Running') is True, 'OOM killed:', state.get('OOMKilled') is True)
    for name, markers in categories.items():
      print(name + ':', any(marker in combined for marker in markers))
    sys.exit(0)
  # HostConfig omits environment values, keys and other application configuration.
  host = json.loads(output('docker', 'inspect', '--format', '{{json .HostConfig}}', container))
  check(host['ReadonlyRootfs'] is True, 'root filesystem must be read-only')
  check('ALL' in host['CapDrop'], 'Linux capabilities must be dropped')
  check('no-new-privileges:true' in host['SecurityOpt'], 'privilege escalation must be disabled')
  check(host['Memory'] == 1024 ** 3, 'memory limit must be 1 GiB')
  check(host['NanoCpus'] == 2_000_000_000, 'CPU limit must be two cores')
  check(host['PidsLimit'] == 256, 'PID limit must be bounded')
  check(any(limit['Name'] == 'core' and limit['Hard'] == 0 and limit['Soft'] == 0
    for limit in host['Ulimits']), 'core dump limit must be zero')
  check('size=64m' in host['Tmpfs']['/tmp'], 'temporary storage must be bounded')
  check('exec' in host['Tmpfs']['/tmp'].split(',') and 'noexec' not in host['Tmpfs']['/tmp'].split(','),
    'temporary native libraries require executable mappings')
  for bindings in host['PortBindings'].values():
    check(all(binding['HostIp'] == '127.0.0.1' for binding in bindings), 'API ports must bind to loopback')
  check(output('docker', 'exec', container, 'id', '-u') == '10001', 'API must run as non-root UID 10001')
  output('docker', 'exec', container, 'test', '-w', '/tmp')

  flags = output('docker', 'exec', container, 'java', '-XX:+PrintFlagsFinal', '-version')
  for name, value in [('HeapDumpOnOutOfMemoryError', 'false'), ('CreateCoredumpOnCrash', 'false'),
    ('DisableAttachMechanism', 'true')]:
    check(bool(re.search(r'\b' + name + r'\s*:?=\s*' + value + r'\b', flags)), 'unexpected JVM diagnostic flag: ' + name)
  check(bool(re.search(r'\bErrorFile\s*:?=\s*/dev/null\b', flags)), 'fatal error files must be suppressed')
  print('PASS: non-root runtime, read-only filesystem, privilege/resource bounds and JVM diagnostic controls')
except Exception:
  print('FAIL: container security controls differ from the reviewed contract (configuration suppressed)', file=sys.stderr)
  sys.exit(1)
