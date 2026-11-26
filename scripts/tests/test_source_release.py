import copy
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import urllib.error
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import dependency_inventory as inventory
import source_release as release
import publish_source_release as publisher


class FakeGitHub:
  repo = 'owner/Keynest'

  def __init__(self, sha, files):
    self.sha, self.files = sha, files
    self.calls, self.tag, self.release = [], None, None
    self.ci_state = 'success'
    self.collision, self.bad_upload, self.lost_ack = False, False, False

  def artifact(self, artifact):
    return self.files.copy()

  def request(self, method, path, data=None, upload=False, missing=False):
    self.calls.append((method, path))
    if path.startswith('actions/workflows/'):
      name = path.split('/')[2]
      return {'workflow_runs': [{'id': 10 if name == 'ci.yml' else 20, 'head_sha': self.sha,
        'event': 'push', 'head_branch': 'main', 'path': '.github/workflows/' + name,
        'status': 'completed', 'conclusion': self.ci_state}]}
    if path.startswith('actions/runs/') and '/jobs?' in path:
      names = release.REQUIRED_JOBS if '/10/' in path else {'Source dependency inventory and OSV'}
      return {'jobs': [{'name': name, 'status': 'completed', 'conclusion': 'success'} for name in names]}
    if path.startswith('actions/runs/') and '/artifacts?' in path:
      return {'artifacts': [{'id': 30, 'name': 'dependency-security-' + self.sha,
        'workflow_run': {'id': 20, 'head_sha': self.sha}}]}
    if method == 'GET' and path.startswith('git/ref/tags/'):
      return self.tag
    if method == 'GET' and (path.startswith('releases/tags/') or path == 'releases/40'):
      return copy.deepcopy(self.release)
    if method == 'POST' and path == 'git/refs':
      if self.collision:
        raise ValueError('concurrent tag creation')
      self.tag = {'ref': data['ref'], 'object': {'sha': data['sha'], 'type': 'commit'}}
      return self.tag
    if method == 'POST' and path == 'releases':
      self.release = {**data, 'id': 40, 'assets': []}
      return copy.deepcopy(self.release)
    if method == 'POST' and path.startswith('releases/40/assets?'):
      assert upload
      name = path.split('name=')[1]
      asset = {'name': name, 'state': 'uploaded', 'digest': 'sha256:' + release.digest(data)}
      if self.bad_upload:
        asset['digest'] = 'sha256:' + '0' * 64
      self.release['assets'].append(asset)
      return asset
    if method == 'PATCH' and path == 'releases/40':
      self.release.update(data)
      if self.lost_ack:
        raise OSError('lost publication acknowledgment')
      return copy.deepcopy(self.release)
    raise AssertionError((method, path))


class SourceReleaseTest(unittest.TestCase):
  def setUp(self):
    self.temp = tempfile.TemporaryDirectory()
    self.addCleanup(self.temp.cleanup)
    self.base = Path(self.temp.name)
    self.root = self.base / 'source'
    self.root.mkdir()
    self.output = self.base / 'bundle'
    self.version = '0.1.1'
    ns = 'http://maven.apache.org/POM/4.0.0'
    files = {'pom.xml': f'<project xmlns="{ns}"><version>0.1.1</version></project>',
      'examples/external-consumer/pom.xml': f'<project xmlns="{ns}"><version>0.1.1</version>'
        '<properties><keynest.version>0.1.1</keynest.version></properties></project>',
      'package.json': json.dumps({'version': '0.1.1', 'scripts': {
        'start-api': 'java -jar apps/manager-api/target/manager-api-0.1.1.jar'}}),
      'package-lock.json': json.dumps({'version': '0.1.1', 'packages': {'': {'version': '0.1.1'}}}),
      'apps/manager-web/package.json': json.dumps({'version': '0.1.1'}),
      'apps/manager-web/package-lock.json': json.dumps({'version': '0.1.1', 'packages': {
        '': {'version': '0.1.1'}, 'node_modules/example': {'version': '1.0.0', 'dev': True}}}),
      'deploy/Dockerfile.api': 'COPY --from=build /src/apps/manager-api/target/manager-api-0.1.1.jar app.jar',
      'CHANGELOG.md': '## 0.1.1 — 2026-11-24\n', 'docs/releases/0.1.1.md': '# Keynest 0.1.1\n',
      'scripts/dependency_inventory.py': '\n'.join(name + ' = ' + repr(getattr(inventory, name))
        for name in ('MAVEN_PLUGIN', 'NPM_VERSION', 'OSV_VERSION', 'OSV_SHA256'))}
    for path in inventory.INPUTS[1:5]:
      files[path] = f'<project xmlns="{ns}"><parent><version>0.1.1</version></parent></project>'
    for name, text in files.items():
      path = self.root / name
      path.parent.mkdir(parents=True, exist_ok=True)
      path.write_text(text)
    self.git('init', '-q')
    self.git('add', '.')
    self.git('-c', 'user.name=Release fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture')
    self.sha = self.git('rev-parse', 'HEAD').strip()
    java = ['pkg:maven/dev.keynest/' + name + '@0.1.1' for name in
            ('keynest-seal', 'keynest-policy', 'keynest-generate', 'manager-api')]
    web = ['pkg:npm/example@1.0.0']
    def bom(name, coordinates):
      return {'bomFormat': 'CycloneDX', 'specVersion': '1.6',
        'metadata': {'component': {'name': name, 'version': '0.1.1', 'bom-ref': 'root'}},
        'components': [{'bom-ref': str(i), 'purl': p} for i, p in enumerate(coordinates)],
        'dependencies': [{'ref': 'root', 'dependsOn': [str(i) for i in range(len(coordinates))]}]}
    self.files = {'java.cdx.json': json.dumps(bom('keynest', java)).encode(),
                  'web.cdx.json': json.dumps(bom('manager-web', web)).encode()}
    manifest = {'schemaVersion': 1, 'sourceCommit': self.sha,
      'inputs': {name: release.digest((self.root / name).read_bytes()) for name in inventory.INPUTS},
      'tools': {'mavenPlugin': inventory.MAVEN_PLUGIN, 'npm': inventory.NPM_VERSION,
                'osvScannerVersion': inventory.OSV_VERSION, 'osvScannerLinuxAmd64Sha256': inventory.OSV_SHA256},
      'inventories': {name: {'sha256': release.digest(value), 'components': len(json.loads(value)['components']),
        'uniquePackageUrls': len(json.loads(value)['components'])} for name, value in self.files.items()}}
    self.files['manifest.json'] = json.dumps(manifest).encode()
    self.files['osv.json'] = json.dumps({'results': [{'source': {'path': name, 'type': 'sbom'},
      'packages': [{'package': {'purl': p}} for p in coordinates]} for name, coordinates in
      [('java.cdx.json', java), ('web.cdx.json', web)]]}).encode()
    self.api = FakeGitHub(self.sha, self.files)
    self.environment = {'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_REF': 'refs/heads/main',
      'GITHUB_SHA': self.sha, 'GITHUB_ACTOR': 'owner', 'GITHUB_TRIGGERING_ACTOR': 'owner',
      'GITHUB_REPOSITORY': self.api.repo, 'PUBLISH_REQUESTED': 'true'}

  def git(self, *args):
    return subprocess.check_output(['git', *args], cwd=self.root, text=True, stderr=subprocess.DEVNULL)

  def prepare(self):
    return release.prepare(self.root, self.version, self.sha, self.api, self.output)

  def publish(self):
    return publisher.publish(self.root, self.version, self.sha, self.api, self.output, self.environment)

  def writes(self):
    return [call for call in self.api.calls if call[0] != 'GET']

  def test_preparation_is_read_only_and_complete_then_owner_publication_succeeds(self):
    self.prepare()
    self.assertEqual(self.writes(), [])
    self.assertEqual({p.name for p in self.output.iterdir()}, set(release.BUNDLE_FILES))
    result = self.publish()
    self.assertEqual(result['sourceCommit'], self.sha)
    self.assertFalse(self.api.release['draft'])
    self.assertEqual({a['name'] for a in self.api.release['assets']}, set(release.PUBLIC_FILES))
    self.assertEqual(self.api.tag['object']['sha'], self.sha)

  def test_pr_branch_nonowner_rerun_dryrun_and_stale_source_cannot_publish(self):
    for key, value in [('GITHUB_EVENT_NAME', 'pull_request'), ('GITHUB_REF', 'refs/heads/test'),
                       ('GITHUB_ACTOR', 'contributor'), ('GITHUB_TRIGGERING_ACTOR', 'contributor'),
                       ('PUBLISH_REQUESTED', 'false'), ('GITHUB_SHA', '0' * 40)]:
      with self.subTest(key=key):
        env = {**self.environment, key: value}
        with self.assertRaises(ValueError):
          publisher.publish(self.root, self.version, self.sha, self.api, self.output, env)
        self.assertEqual(self.api.calls, [])

  def test_bad_version_dirty_tree_and_mismatched_metadata_fail_before_network(self):
    for version, sha in [('01.1.0', self.sha), ('0.1.1;echo', self.sha), ('0.1.1-rc.1', self.sha),
                          ('0.1.1', 'main'), ('0.1.1', '0' * 40), ('0.1.2', self.sha)]:
      with self.subTest(version=version), self.assertRaises(ValueError):
        release.check_source(self.root, version, sha)
    (self.root / 'pom.xml').write_text('changed')
    with self.assertRaises(subprocess.CalledProcessError):
      self.prepare()
    self.assertEqual(self.api.calls, [])

  def test_failed_or_changed_ci_blocks_preparation_and_publication(self):
    self.api.ci_state = 'failure'
    with self.assertRaises(ValueError):
      self.prepare()
    self.api.ci_state = 'success'
    self.prepare()
    self.api.ci_state = 'failure'
    with self.assertRaises(ValueError):
      self.publish()
    self.assertEqual(self.writes(), [])

  def test_missing_job_and_non_main_runs_are_rejected(self):
    original = self.api.request
    def response(method, path, *args, **kwargs):
      data = original(method, path, *args, **kwargs)
      if '/jobs?' in path:
        data['jobs'].pop()
      return data
    self.api.request = response
    with self.assertRaises(ValueError):
      self.prepare()
    def branch_response(method, path, *args, **kwargs):
      data = original(method, path, *args, **kwargs)
      if 'workflow_runs' in data:
        data['workflow_runs'][0]['head_branch'] = 'feature'
      return data
    self.api.request = branch_response
    with self.assertRaises(ValueError):
      self.prepare()

  def test_rehearsal_uses_source_tool_versions_without_executing_source(self):
    path = self.root / 'scripts/dependency_inventory.py'
    original = path.read_text()
    path.write_text(original.replace(repr(inventory.OSV_VERSION), repr('synthetic-older-version'))
                    + '\nraise AssertionError("source code must not execute")\n')
    manifest = json.loads(self.files['manifest.json'])
    manifest['tools']['osvScannerVersion'] = 'synthetic-older-version'
    self.files['manifest.json'] = json.dumps(manifest).encode()
    # This directly tests source-bound inventory validation; checkout cleanliness
    # is tested separately and rejects tracked changes in prepare/publish.
    release.validate_inventories(self.root, self.version, self.sha, self.files)
    manifest['tools']['osvScannerVersion'] = inventory.OSV_VERSION
    self.files['manifest.json'] = json.dumps(manifest).encode()
    with self.assertRaises(ValueError):
      release.validate_inventories(self.root, self.version, self.sha, self.files)

  def test_incomplete_or_vulnerable_scans_and_wrong_manifest_are_rejected(self):
    good = self.files.copy()
    for failure in ('missing', 'finding', 'source', 'digest'):
      self.files.clear()
      self.files.update(good)
      if failure in ('missing', 'finding'):
        data = json.loads(self.files['osv.json'])
        if failure == 'missing':
          data['results'][0]['packages'].pop()
        else:
          data['results'][0]['packages'][0]['vulnerabilities'] = [{'id': 'synthetic-advisory'}]
        self.files['osv.json'] = json.dumps(data).encode()
      else:
        data = json.loads(self.files['manifest.json'])
        if failure == 'source': data['sourceCommit'] = '0' * 40
        else: data['inputs']['pom.xml'] = '0' * 64
        self.files['manifest.json'] = json.dumps(data).encode()
      with self.subTest(failure=failure), self.assertRaises(ValueError):
        self.prepare()
      self.assertFalse(self.output.exists())

  def test_extra_files_and_tampered_bundle_cannot_be_uploaded(self):
    self.prepare()
    extra = self.output / '.unexpected'
    extra.write_text('synthetic extra file')
    with self.assertRaises(ValueError): self.publish()
    extra.unlink()
    (self.output / 'osv.json').write_text('{}')
    with self.assertRaises(ValueError): self.publish()
    self.assertEqual(self.writes(), [])

  def test_existing_tag_or_release_and_concurrent_tag_are_never_overwritten(self):
    self.prepare()
    self.api.tag = {'object': {'sha': '0' * 40}}
    with self.assertRaises(ValueError): self.publish()
    self.assertEqual(self.writes(), [])
    self.api.tag = None
    self.api.release = {'id': 41}
    with self.assertRaises(ValueError): self.publish()
    self.assertEqual(self.writes(), [])
    self.api.release = None
    self.api.collision = True
    with self.assertRaises(ValueError): self.publish()
    self.assertEqual(self.writes(), [('POST', 'git/refs')])

  def test_bad_upload_stays_draft_without_cleanup_or_publication(self):
    self.prepare()
    self.api.bad_upload = True
    with self.assertRaises(ValueError): self.publish()
    self.assertTrue(self.api.release['draft'])
    self.assertTrue(all(method != 'DELETE' and method != 'PATCH' for method, _ in self.writes()))

  def test_lost_publication_ack_is_not_retried_or_rolled_back(self):
    self.prepare()
    self.api.lost_ack = True
    with self.assertRaises(OSError): self.publish()
    self.assertEqual(sum(method == 'PATCH' for method, _ in self.writes()), 1)
    self.assertTrue(all(method != 'DELETE' for method, _ in self.writes()))

  def test_artifact_redirect_does_not_forward_github_credential(self):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as archive:
      for name, content in self.files.items(): archive.writestr(name, content)
    data = stream.getvalue()
    api = release.GitHub('owner/Keynest', 'synthetic-unit-credential')
    requests = []
    class Opener:
      def open(self, request, timeout):
        requests.append(request)
        if len(requests) == 1:
          raise urllib.error.HTTPError(request.full_url, 302, 'redirect',
            {'Location': 'https://artifact.example.invalid/synthetic'}, None)
        return io.BytesIO(data)
    api.opener = Opener()
    files = api.artifact({'id': 30, 'expired': False, 'size_in_bytes': len(data),
                         'digest': 'sha256:' + hashlib.sha256(data).hexdigest()})
    self.assertEqual(files, self.files)
    self.assertIsNotNone(requests[0].get_header('Authorization'))
    self.assertIsNone(requests[1].get_header('Authorization'))

  def test_archive_digest_unexpected_files_and_expiry_are_rejected(self):
    for failure in ('digest', 'extra', 'expired'):
      stream = io.BytesIO()
      with zipfile.ZipFile(stream, 'w') as archive:
        for name, content in self.files.items(): archive.writestr(name, content)
        if failure == 'extra': archive.writestr('../unexpected', 'synthetic')
      data = stream.getvalue()
      class Opener:
        calls = 0
        def open(self, request, timeout):
          self.calls += 1
          if self.calls == 1:
            raise urllib.error.HTTPError(request.full_url, 302, 'redirect',
              {'Location': 'https://artifact.example.invalid/synthetic'}, None)
          return io.BytesIO(data)
      api = release.GitHub('owner/Keynest', 'synthetic-unit-credential')
      api.opener = Opener()
      with self.subTest(failure=failure), self.assertRaises(ValueError):
        api.artifact({'id': 30, 'expired': failure == 'expired', 'size_in_bytes': len(data),
          'digest': 'sha256:' + ('0' * 64 if failure == 'digest' else release.digest(data))})

  def test_github_mutation_error_is_sanitized_and_not_retried(self):
    class Opener:
      calls = 0
      def open(self, request, timeout):
        self.calls += 1
        raise urllib.error.HTTPError(request.full_url, 429, 'synthetic', {}, None)
    api = release.GitHub('owner/Keynest', 'synthetic-unit-credential')
    api.opener = Opener()
    with self.assertRaisesRegex(ValueError, '^GitHub request failed with HTTP 429$'):
      api.request('POST', 'git/refs', {'ref': 'refs/tags/v0.1.1', 'sha': self.sha})
    self.assertEqual(api.opener.calls, 1)


if __name__ == '__main__':
  unittest.main()
