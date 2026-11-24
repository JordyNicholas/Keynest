#!/usr/bin/env python3
"""Prepare a bounded source-release bundle from successful same-SHA main CI."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

import dependency_inventory as inventory

ASSETS = ('java.cdx.json', 'web.cdx.json', 'manifest.json', 'osv.json')
PUBLIC_FILES = (*ASSETS, 'SHA256SUMS.txt')
BUNDLE_FILES = (*PUBLIC_FILES, 'release-notes.md', 'release.json')
REQUIRED_JOBS = {'Maven (Java 21)', 'Maven (Java 25)', 'Angular build',
                 'External consumer (Java 21)', 'External consumer (Java 25)',
                 'Packaged Compose smoke', 'Hosted Compose smoke'}
MAX_FILE = 32 * 1024 * 1024


class ReleaseError(ValueError):
  """A fixed diagnostic that is safe to expose without request/credential data."""


def require(condition, message):
  if not condition:
    raise ReleaseError(message)


def digest(data):
  return hashlib.sha256(data).hexdigest()


def read(path):
  require(not path.is_symlink() and path.is_file() and path.stat().st_size <= MAX_FILE,
          'missing, linked or oversized release input')
  return path.read_bytes()


def repository(value):
  require(bool(re.fullmatch(r'[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+', value))
          and value.split('/')[1] not in ('.', '..'), 'invalid repository')
  return value


def check_source(root, version, sha):
  require(bool(re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', version))
          and len(version) <= 32, 'expected a stable numeric release version')
  require(bool(re.fullmatch('[0-9a-f]{40}', sha)), 'expected a full source SHA')
  require(subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == sha,
          'checkout differs from expected source')
  subprocess.run(['git', 'diff', '--quiet', 'HEAD', '--'], cwd=root, check=True)
  ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
  for path in ('pom.xml', 'libs/keynest-seal/pom.xml', 'libs/keynest-policy/pom.xml',
               'libs/keynest-generate/pom.xml', 'apps/manager-api/pom.xml',
               'examples/external-consumer/pom.xml'):
    xml = ET.fromstring(read(root / path))
    expression = 'm:version' if path in ('pom.xml', 'examples/external-consumer/pom.xml') else 'm:parent/m:version'
    require(xml.find(expression, ns).text == version, 'Maven release versions disagree')
    if path.startswith('examples/'):
      require(xml.find('m:properties/m:keynest.version', ns).text == version, 'consumer version disagrees')
  for path in ('package.json', 'package-lock.json', 'apps/manager-web/package.json',
               'apps/manager-web/package-lock.json'):
    data = json.loads(read(root / path))
    require(data['version'] == version, 'npm release versions disagree')
    if 'packages' in data:
      require(data['packages']['']['version'] == version, 'npm lock root version disagrees')
  package = json.loads(read(root / 'package.json'))
  jar = f'manager-api-{version}.jar'
  require(package['scripts']['start-api'].endswith('java -jar apps/manager-api/target/' + jar),
          'API launch JAR version disagrees')
  require(f'COPY --from=build /src/apps/manager-api/target/{jar} app.jar' in
          read(root / 'deploy/Dockerfile.api').decode(), 'Docker JAR version disagrees')
  require(bool(re.search(r'^## ' + re.escape(version) + r' — \d{4}-\d{2}-\d{2}$',
                        read(root / 'CHANGELOG.md').decode(), re.MULTILINE)), 'release changelog missing')
  read(root / f'docs/releases/{version}.md')


class NoRedirect(urllib.request.HTTPRedirectHandler):
  def redirect_request(self, req, fp, code, msg, headers, newurl):
    return None


class GitHub:
  """Fixed GitHub endpoints, bounded responses, no retry of state-changing calls."""
  def __init__(self, repo, token):
    self.repo = repository(repo)
    require(bool(token), 'GitHub credential missing')
    self.token = token
    self.opener = urllib.request.build_opener(NoRedirect)

  def request(self, method, path, data=None, upload=False, missing=False):
    host = 'uploads.github.com' if upload else 'api.github.com'
    headers = {'Authorization': 'Bearer ' + self.token, 'Accept': 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2026-03-10',
               'Content-Type': 'text/plain' if upload and path.endswith('SHA256SUMS.txt') else 'application/json'}
    body = data if isinstance(data, bytes) else json.dumps(data).encode() if data is not None else None
    request = urllib.request.Request(f'https://{host}/repos/{self.repo}/{path}',
                                     data=body, method=method, headers=headers)
    try:
      with self.opener.open(request, timeout=45) as response:
        content = response.read(MAX_FILE + 1)
    except urllib.error.HTTPError as error:
      if missing and error.code == 404:
        return None
      raise ReleaseError(f'GitHub request failed with HTTP {error.code}') from None
    require(len(content) <= MAX_FILE, 'oversized GitHub response')
    return json.loads(content)

  def artifact(self, artifact):
    require(not artifact['expired'] and artifact['size_in_bytes'] <= 8 * 1024 * 1024,
            'expired or oversized inventory artifact')
    url = f'https://api.github.com/repos/{self.repo}/actions/artifacts/{int(artifact["id"])}/zip'
    request = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + self.token})
    try:
      self.opener.open(request, timeout=45)
    except urllib.error.HTTPError as error:
      require(error.code == 302, 'inventory download did not return its expected redirect')
      location = error.headers['Location']
    else:
      raise ReleaseError('inventory download did not return its expected redirect')
    # Never forward the GitHub credential to the signed artifact destination.
    from urllib.parse import urlsplit
    destination = urlsplit(location)
    require(destination.scheme == 'https' and bool(destination.hostname) and not destination.username
            and not destination.password, 'unsafe artifact destination')
    with self.opener.open(urllib.request.Request(location), timeout=45) as response:
      data = response.read(8 * 1024 * 1024 + 1)
    require(len(data) <= 8 * 1024 * 1024 and artifact['digest'] == 'sha256:' + digest(data),
            'inventory archive digest disagrees')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
      require(len(archive.infolist()) == len(ASSETS) and set(archive.namelist()) == set(ASSETS),
              'unexpected inventory artifact files')
      require(all(entry.file_size <= MAX_FILE for entry in archive.infolist()), 'oversized inventory file')
      return {name: archive.read(name) for name in ASSETS}


def gates(api, sha):
  result = {}
  for name, required in (('ci.yml', REQUIRED_JOBS),
                         ('dependency-security.yml', {'Source dependency inventory and OSV'})):
    runs = api.request('GET', f'actions/workflows/{name}/runs?event=push&branch=main&per_page=100')['workflow_runs']
    matches = [run for run in runs if run['head_sha'] == sha and run['event'] == 'push'
               and run['head_branch'] == 'main' and run['path'] == '.github/workflows/' + name]
    require(bool(matches), 'same-source main-branch verification run missing')
    run = max(matches, key=lambda item: item['id'])
    require(run['status'] == 'completed' and run['conclusion'] == 'success', 'verification run is not successful')
    jobs = api.request('GET', f'actions/runs/{run["id"]}/jobs?filter=latest&per_page=100')['jobs']
    for job_name in required:
      found = [job for job in jobs if job['name'] == job_name]
      require(len(found) == 1 and found[0]['status'] == 'completed' and found[0]['conclusion'] == 'success',
              'required verification job did not pass')
    result['ci' if name == 'ci.yml' else 'scan'] = run['id']
  return result


def validate_inventories(root, version, sha, files):
  require(set(files) == set(ASSETS), 'unexpected inventory files')
  manifest = json.loads(files['manifest.json'])
  require(manifest['sourceCommit'] == sha and manifest['schemaVersion'] == 1, 'inventory source disagrees')
  require(set(manifest['inputs']) == set(inventory.INPUTS), 'inventory input coverage disagrees')
  for name, expected in manifest['inputs'].items():
    require(digest(read(root / name)) == expected, 'inventory input digest disagrees')
  tools = manifest['tools']
  require(tools['mavenPlugin'] == inventory.MAVEN_PLUGIN and tools['npm'] == inventory.NPM_VERSION
          and tools['osvScannerVersion'] == inventory.OSV_VERSION
          and tools['osvScannerLinuxAmd64Sha256'] == inventory.OSV_SHA256, 'inventory tools disagree')
  require(set(manifest['inventories']) == {'java.cdx.json', 'web.cdx.json'}, 'inventory output coverage disagrees')
  lock = json.loads(read(root / 'apps/manager-web/package-lock.json'))
  required = {'java.cdx.json': {'pkg:maven/dev.keynest/' + name + '@' + version for name in
                              ('keynest-seal', 'keynest-policy', 'keynest-generate', 'manager-api')},
              'web.cdx.json': inventory.lock_coordinates(lock)}
  packages = {}
  for name, root_name in (('java.cdx.json', 'keynest'), ('web.cdx.json', 'manager-web')):
    require(digest(files[name]) == manifest['inventories'][name]['sha256'], 'inventory output digest disagrees')
    bom = json.loads(files[name])
    summary = inventory.validate_bom(bom, root_name, version, required[name])
    require(all(summary[key] == manifest['inventories'][name][key] for key in summary), 'inventory counts disagree')
    packages[name] = {inventory.coordinates(component) for component in bom['components']}
  results = json.loads(files['osv.json'])['results']
  require(isinstance(results, list) and len(results) == 2, 'complete scanner results missing')
  covered = set()
  for result in results:
    name = Path(result['source']['path']).name
    require(name in packages and name not in covered and result['source']['type'] == 'sbom', 'scanner source disagrees')
    covered.add(name)
    entries = result['packages']
    require(isinstance(entries, list) and len(entries) <= 20000, 'invalid scanner package list')
    observed = []
    for entry in entries:
      require(not entry.get('vulnerabilities') and not entry.get('groups'), 'scanner returned advisory findings')
      observed.append(inventory.coordinates(entry['package']))
    require(len(observed) == len(set(observed)) and set(observed) == packages[name], 'scanner package coverage disagrees')
  return manifest


def notes(repo, version, sha, receipt, manifest):
  base = f'https://github.com/{repo}'
  return (f'# Keynest {version}\n\nSource release under Apache-2.0.\n\n'
          f'[Changes, setup, upgrade and limitations]({base}/blob/{sha}/docs/releases/{version}.md) · '
          f'[Changelog]({base}/blob/{sha}/CHANGELOG.md)\n\n'
          f'Source commit: `{sha}`\n\n'
          f'[Application CI]({base}/actions/runs/{receipt["ci"]}) passed all seven required jobs. '
          f'[Inventory and OSV]({base}/actions/runs/{receipt["scan"]}) passed on that same source. '
          f'Validated Java {manifest["inventories"]["java.cdx.json"]["components"]} / '
          f'Web {manifest["inventories"]["web.cdx.json"]["components"]} package coordinates; no advisory matches or suppressions.\n\n'
          'Attached: Java/Web CycloneDX inventories, source/input/output manifest, OSV report and unsigned SHA-256 checksums. '
          'Matching reflects its database/date and does not guarantee absence of vulnerabilities. '
          'Scope excludes OS/base images, generator/scanner toolchains and the standalone example’s separate graph.\n\n'
          'Back up PostgreSQL and separately retain its matching vault key/key ID before upgrading. '
          'Existing custody, browser-token, metadata and deployment limits remain in the linked notes. '
          'No hosted service, registry artifact, signed provenance, new manual-platform verification or independent audit is claimed.\n')


def prepare(root, version, sha, api, output):
  check_source(root, version, sha)
  receipt = gates(api, sha)
  artifacts = api.request('GET', f'actions/runs/{receipt["scan"]}/artifacts?per_page=100')['artifacts']
  selected = [item for item in artifacts if item['name'] == 'dependency-security-' + sha]
  require(len(selected) == 1 and selected[0]['workflow_run']['id'] == receipt['scan']
          and selected[0]['workflow_run']['head_sha'] == sha, 'inventory artifact identity disagrees')
  files = api.artifact(selected[0])
  manifest = validate_inventories(root, version, sha, files)
  files['SHA256SUMS.txt'] = ''.join(digest(files[name]) + '  ' + name + '\n' for name in ASSETS).encode()
  files['release-notes.md'] = notes(api.repo, version, sha, receipt, manifest).encode()
  record = {'schemaVersion': 1, 'repository': api.repo, 'version': version, 'sourceCommit': sha,
            'verificationRuns': receipt, 'files': {name: digest(content) for name, content in files.items()}}
  files['release.json'] = (json.dumps(record, indent=2) + '\n').encode()
  output.mkdir(parents=True, exist_ok=False)
  for name, content in files.items():
    (output / name).write_bytes(content)
  return record


def verify_bundle(root, version, sha, api, directory):
  check_source(root, version, sha)
  require(set(item.name for item in directory.iterdir()) == set(BUNDLE_FILES), 'unexpected release bundle files')
  files = {name: read(directory / name) for name in BUNDLE_FILES}
  record = json.loads(files['release.json'])
  require(record['schemaVersion'] == 1 and record['repository'] == api.repo and record['version'] == version
          and record['sourceCommit'] == sha, 'release bundle identity disagrees')
  require(set(record['files']) == set(BUNDLE_FILES) - {'release.json'}, 'release bundle coverage disagrees')
  require(all(digest(files[name]) == value for name, value in record['files'].items()), 'release bundle digest disagrees')
  manifest = validate_inventories(root, version, sha, {name: files[name] for name in ASSETS})
  require(record['verificationRuns'] == gates(api, sha), 'verification evidence changed')
  require(files['release-notes.md'] == notes(api.repo, version, sha, record['verificationRuns'], manifest).encode(),
          'release notes disagree with verified evidence')
  expected = ''.join(digest(files[name]) + '  ' + name + '\n' for name in ASSETS).encode()
  require(files['SHA256SUMS.txt'] == expected, 'checksum list disagrees')
  return record


def main():
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('--source', type=Path, default=Path('.'))
  parser.add_argument('--version', required=True)
  parser.add_argument('--expected-sha', required=True)
  parser.add_argument('--output', type=Path, required=True)
  args = parser.parse_args()
  if os.environ['GITHUB_EVENT_NAME'] == 'workflow_dispatch':
    require(os.environ['GITHUB_REF'] == 'refs/heads/main' and os.environ['GITHUB_SHA'] == args.expected_sha,
            'manual preparation requires the expected main dispatch SHA')
  api = GitHub(os.environ['GITHUB_REPOSITORY'], os.environ['GH_TOKEN'])
  record = prepare(args.source.resolve(), args.version, args.expected_sha, api, args.output)
  print(json.dumps({'prepared': True, 'version': record['version'], 'sourceCommit': record['sourceCommit'],
                    'verificationRuns': record['verificationRuns'], 'publication': False}))


if __name__ == '__main__':
  try:
    main()
  except ReleaseError as error:
    raise SystemExit('FAIL: ' + str(error)) from None
  except (OSError, ValueError, KeyError, TypeError, AttributeError, ET.ParseError,
          zipfile.BadZipFile, subprocess.SubprocessError):
    raise SystemExit('FAIL: source-release preparation; inspect gate status and source/bundle metadata') from None
