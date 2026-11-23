#!/usr/bin/env python3
"""Validate dependency inventories and bind their hashes to a clean source commit."""
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote
import xml.etree.ElementTree as ET

OUTPUT = Path('target/dependency-security')
MAVEN_PLUGIN = 'org.cyclonedx:cyclonedx-maven-plugin:2.9.3'
NPM_VERSION = '11.19.1'
OSV_VERSION = '2.7.1'
OSV_SHA256 = 'fb7dab19a8bd9bbe5c3189f09d4b7ee1820c9553a23bac202648a56efd2f6ee8'
INPUTS = ['pom.xml', 'libs/keynest-seal/pom.xml', 'libs/keynest-policy/pom.xml',
          'libs/keynest-generate/pom.xml', 'apps/manager-api/pom.xml',
          'apps/manager-web/package.json', 'apps/manager-web/package-lock.json']


def digest(path):
  return hashlib.sha256(path.read_bytes()).hexdigest()


def coordinates(component):
  value = component.get('purl')
  if not isinstance(value, str) or not value.startswith('pkg:'):
    raise ValueError('component lacks a package URL')
  return unquote(value.split('?', 1)[0])


def validate_bom(bom, root_name, version, required):
  if bom.get('bomFormat') != 'CycloneDX' or bom.get('specVersion') not in ('1.5', '1.6'):
    raise ValueError('unsupported inventory format')
  root = bom.get('metadata', {}).get('component', {})
  if root.get('name') != root_name or root.get('version') != version:
    raise ValueError('inventory root does not match source')
  components = bom.get('components')
  if not isinstance(components, list) or not 1 <= len(components) <= 20000:
    raise ValueError('inventory has no bounded component list')
  refs = {root.get('bom-ref')}
  if not all(isinstance(ref, str) and ref for ref in refs):
    raise ValueError('missing root reference')
  packages = set()
  for component in components:
    if not isinstance(component, dict):
      raise ValueError('invalid component')
    ref = component.get('bom-ref')
    if not isinstance(ref, str) or not ref or ref in refs:
      raise ValueError('missing or repeated component reference')
    refs.add(ref)
    packages.add(coordinates(component))
  if not required <= packages:
    raise ValueError('inventory omits expected source dependencies')
  graph = bom.get('dependencies')
  if not isinstance(graph, list) or not graph:
    raise ValueError('missing dependency graph')
  graph_refs = set()
  for edge in graph:
    if not isinstance(edge, dict) or edge.get('ref') not in refs or edge['ref'] in graph_refs:
      raise ValueError('invalid dependency reference')
    graph_refs.add(edge['ref'])
    targets = edge.get('dependsOn', [])
    if not isinstance(targets, list) or any(target not in refs for target in targets):
      raise ValueError('dangling dependency reference')
  if root['bom-ref'] not in graph_refs:
    raise ValueError('missing root dependency graph')
  return {'components': len(components), 'uniquePackageUrls': len(packages)}


def lock_coordinates(lock):
  result = set()
  for path, entry in lock['packages'].items():
    if not path or entry.get('link'):
      continue
    name = entry.get('name') or path.rsplit('node_modules/', 1)[-1]
    version = entry.get('version')
    if not isinstance(version, str) or not version:
      raise ValueError('lock entry lacks a version')
    result.add('pkg:npm/' + name + '@' + version)
  return result


def load_bom(path):
  if not path.is_file() or path.stat().st_size > 32 * 1024 * 1024:
    raise ValueError('missing or oversized inventory')
  return json.loads(path.read_text())


def main():
  sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
  if not re.fullmatch('[0-9a-f]{40}', sha):
    raise ValueError('invalid source commit')
  subprocess.run(['git', 'diff', '--quiet', 'HEAD', '--'], check=True)
  npm_version = subprocess.check_output(['npm', '--version'], text=True).strip()
  if npm_version != NPM_VERSION:
    raise ValueError('inventory generation requires the pinned npm version')
  ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
  version = ET.parse('pom.xml').getroot().find('m:version', ns).text
  web = json.loads(Path('apps/manager-web/package.json').read_text())
  lock = json.loads(Path('apps/manager-web/package-lock.json').read_text())
  if web['version'] != lock['version'] or lock['packages']['']['version'] != web['version']:
    raise ValueError('web source and lock versions disagree')
  java_required = {'pkg:maven/dev.keynest/' + name + '@' + version for name in
                   ('keynest-seal', 'keynest-policy', 'keynest-generate', 'manager-api')}
  summaries = {
    'java.cdx.json': validate_bom(load_bom(OUTPUT / 'java.cdx.json'), 'keynest', version, java_required),
    'web.cdx.json': validate_bom(load_bom(OUTPUT / 'web.cdx.json'), 'manager-web', web['version'], lock_coordinates(lock)),
  }
  manifest = {
    'schemaVersion': 1, 'sourceCommit': sha,
    'scope': {'java': 'reactor compile/provided/runtime/system/test dependencies',
              'web': 'lockfile dependencies including dev/optional/peer',
              'excluded': 'OS/base image, scanner/generator toolchains, standalone consumer example'},
    'tools': {'mavenPlugin': MAVEN_PLUGIN, 'osvScannerVersion': OSV_VERSION,
              'osvScannerLinuxAmd64Sha256': OSV_SHA256,
              'node': subprocess.check_output(['node', '--version'], text=True).strip(),
              'npm': npm_version},
    'inputs': {name: digest(Path(name)) for name in INPUTS},
    'inventories': {name: {'sha256': digest(OUTPUT / name), **summary} for name, summary in summaries.items()},
    'vulnerabilityStatus': 'not established by inventory generation; inspect the separate OSV step and report',
  }
  (OUTPUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
  print('PASS: source-bound Java and Web inventories; no vulnerability-clean claim')
  print(json.dumps({'sourceCommit': sha, 'inventories': summaries}))


if __name__ == '__main__':
  try:
    main()
  except (OSError, ValueError, KeyError, TypeError, AttributeError, ET.ParseError, subprocess.SubprocessError):
    print('FAIL: dependency inventory validation', file=sys.stderr)
    sys.exit(1)
