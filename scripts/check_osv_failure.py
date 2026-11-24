#!/usr/bin/env python3
"""Rehearse matcher unavailability without treating it as clean or as findings."""
import os
import subprocess
import sys

environment = dict(os.environ)
for name in ('HTTP_PROXY', 'HTTPS_PROXY', 'http_proxy', 'https_proxy'):
  environment[name] = 'http://127.0.0.1:9'
for name in ('NO_PROXY', 'no_proxy'):
  environment[name] = ''
command = ['target/dependency-security/osv-scanner', 'scan', 'source',
           '--sbom=target/dependency-security/web.cdx.json', '--no-resolve',
           '--format=json', '--output-file=target/dependency-security/unavailable-osv.json']
try:
  result = subprocess.run(command, env=environment, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=60, check=False)
except (OSError, subprocess.TimeoutExpired):
  print('FAIL: scanner unavailability rehearsal did not complete', file=sys.stderr)
  sys.exit(1)
if result.returncode < 127:
  print('FAIL: unavailable matcher was not classified as a scanner error', file=sys.stderr)
  sys.exit(1)
print('PASS: unavailable matcher produced scanner error, exit=' + str(result.returncode))
