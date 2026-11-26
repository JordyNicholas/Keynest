#!/usr/bin/env python3
"""Owner-dispatched publication only; never overwrite or retry a release mutation."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

from source_release import GitHub, PUBLIC_FILES, ReleaseError, digest, read, require, verify_bundle


def authorize(environment, sha):
  owner = environment['GITHUB_REPOSITORY'].split('/')[0]
  require(environment.get('GITHUB_EVENT_NAME') == 'workflow_dispatch'
          and environment.get('GITHUB_REF') == 'refs/heads/main'
          and environment.get('GITHUB_SHA') == sha
          and environment.get('GITHUB_ACTOR') == owner
          and environment.get('GITHUB_TRIGGERING_ACTOR') == owner
          and environment.get('PUBLISH_REQUESTED') == 'true',
          'publication requires an explicit owner dispatch on the expected main SHA')


def assets_match(release, expected):
  assets = release['assets']
  require(len(assets) == len(expected) and {item['name'] for item in assets} == set(expected),
          'release asset coverage disagrees')
  for item in assets:
    require(item['state'] == 'uploaded' and item['digest'] == 'sha256:' + expected[item['name']],
            'uploaded release asset digest disagrees')


def publish(root, version, sha, api, directory, environment):
  authorize(environment, sha)
  require(environment['GITHUB_REPOSITORY'] == api.repo, 'publication repository disagrees')
  record = verify_bundle(root, version, sha, api, directory)
  tag = 'v' + version
  require(api.request('GET', 'git/ref/tags/' + tag, missing=True) is None, 'release tag already exists')
  require(api.request('GET', 'releases/tags/' + tag, missing=True) is None, 'release already exists')
  # Atomic create rejects a concurrent tag; no PATCH, force or deletion of refs.
  created = api.request('POST', 'git/refs', {'ref': 'refs/tags/' + tag, 'sha': sha})
  require(created['ref'] == 'refs/tags/' + tag and created['object']['sha'] == sha,
          'created tag target disagrees')
  body = read(directory / 'release-notes.md').decode()
  release = api.request('POST', 'releases', {'tag_name': tag, 'target_commitish': sha,
                       'name': 'Keynest ' + version, 'body': body, 'draft': True, 'prerelease': False})
  release_id = int(release['id'])
  require(release_id > 0, 'invalid draft release identifier')
  expected = {name: record['files'][name] for name in PUBLIC_FILES}
  for name in PUBLIC_FILES:
    data = read(directory / name)
    require(digest(data) == expected[name], 'release asset changed before upload')
    uploaded = api.request('POST', f'releases/{release_id}/assets?name={name}', data, upload=True)
    assets_match({'assets': [uploaded]}, {name: expected[name]})
  draft = api.request('GET', f'releases/{release_id}')
  require(draft['draft'] and not draft['prerelease'] and draft['tag_name'] == tag
          and draft['target_commitish'] == sha and draft['body'] == body, 'draft release identity disagrees')
  assets_match(draft, expected)
  target = api.request('GET', 'git/ref/tags/' + tag)
  require(target['object']['type'] == 'commit' and target['object']['sha'] == sha, 'tag changed before publication')
  api.request('PATCH', f'releases/{release_id}', {'draft': False, 'make_latest': 'true'})
  published = api.request('GET', 'releases/tags/' + tag)
  require(not published['draft'] and not published['prerelease'] and published['target_commitish'] == sha
          and published['tag_name'] == tag and published['id'] == release_id
          and published['body'] == body, 'published release identity disagrees')
  assets_match(published, expected)
  target = api.request('GET', 'git/ref/tags/' + tag)
  require(target['object']['type'] == 'commit' and target['object']['sha'] == sha,
          'published tag target disagrees')
  return {'version': version, 'sourceCommit': sha, 'releaseId': release_id,
          'url': f'https://github.com/{api.repo}/releases/tag/{tag}'}


def main():
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('--version', required=True)
  parser.add_argument('--expected-sha', required=True)
  parser.add_argument('--bundle', type=Path, required=True)
  args = parser.parse_args()
  # Guard before constructing a client, looking up credentials, or making API calls.
  authorize(os.environ, args.expected_sha)
  api = GitHub(os.environ['GITHUB_REPOSITORY'], os.environ['GH_TOKEN'])
  result = publish(Path('.').resolve(), args.version, args.expected_sha, api, args.bundle, os.environ)
  print(json.dumps(result))


if __name__ == '__main__':
  try:
    main()
  except ReleaseError as error:
    raise SystemExit('FAIL: ' + str(error) + '; inspect existing tag/draft/release before recovery. '
                     'No mutation was automatically retried or rolled back.') from None
  except (OSError, ValueError, KeyError, TypeError, AttributeError, ET.ParseError, subprocess.SubprocessError):
    raise SystemExit('FAIL: source-release publication; inspect existing tag/draft/release before any manual recovery. '
                     'No mutation was automatically retried or rolled back.') from None
