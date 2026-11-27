"""Exercise only the disposable local Compose API; never print secret payloads."""

import argparse
import base64
import json
import secrets
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener


BASE = 'http://127.0.0.1:8080'
API = '/api/v1'


class SmokeFailure(Exception):
  pass


class NoRedirect(HTTPRedirectHandler):
  def redirect_request(self, req, fp, code, msg, headers, newurl):
    return None


# Keep test bearers on the loopback API even if proxy environment variables exist.
opener = build_opener(ProxyHandler({}), NoRedirect())


def check(condition, step):
  if not condition:
    raise SmokeFailure(step)


def check_security_headers(headers):
  expected = {
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'X-Frame-Options': 'DENY',
    'Content-Security-Policy': "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
  }
  for name, value in expected.items():
    check(headers.get_all(name) == [value], 'API response security header missing or changed: ' + name)


def request(method, path, body=None, token=None, binary=False):
  headers = {'Content-Type': 'application/json'}
  if token:
    headers['Authorization'] = 'Bearer ' + token
  data = None if body is None else json.dumps(body).encode('utf-8')
  req = Request(BASE + path, data=data, headers=headers, method=method)
  try:
    with opener.open(req, timeout=5) as response:
      check_security_headers(response.headers)
      return response.status, response.headers, response.read() if binary else json.load(response)
  except HTTPError as error:
    # Error payloads can contain user data; callers need only the status.
    status = error.code
    response_headers = error.headers
    error.close()
    check_security_headers(response_headers)
    return status, response_headers, None


def expect(method, path, body=None, token=None, status=200):
  actual, headers, result = request(method, path, body, token)
  check(actual == status, method + ' ' + path.split('?')[0] + ' returned unexpected HTTP status')
  return headers, result


def wait_until_ready():
  deadline = time.monotonic() + 120
  while time.monotonic() < deadline:
    try:
      status, _, body = request('GET', '/actuator/health/readiness')
      if status == 200 and body == {'status': 'UP'}:
        return
    except (URLError, TimeoutError, OSError):
      pass
    time.sleep(1)
  raise SmokeFailure('readiness did not become UP within 120 seconds')


def check_cors(allowed_origin):
  url = BASE + API + '/vault/items'
  for origin in (allowed_origin, 'https://unlisted.example.invalid'):
    preflight = Request(url, method='OPTIONS', headers={
      'Origin': origin,
      'Access-Control-Request-Method': 'POST',
      'Access-Control-Request-Headers': 'authorization, content-type',
    })
    try:
      with opener.open(preflight, timeout=5) as response:
        check_security_headers(response.headers)
        check(origin == allowed_origin and 200 <= response.status < 300, 'unlisted CORS origin accepted')
        check(response.headers.get('Access-Control-Allow-Origin') == origin, 'CORS origin must match exactly')
        methods = response.headers.get('Access-Control-Allow-Methods', '').replace(' ', '').split(',')
        check('POST' in methods, 'CORS must allow POST')
        headers = response.headers.get('Access-Control-Allow-Headers', '').lower().replace(' ', '').split(',')
        check({'authorization', 'content-type'} <= set(headers), 'CORS must allow bearer and JSON headers')
    except HTTPError as error:
      with error:
        check_security_headers(error.headers)
        check(origin != allowed_origin and error.code == 403, 'unexpected CORS preflight rejection')
        check('Access-Control-Allow-Origin' not in error.headers, 'unlisted origin must receive no CORS grant')


def run(cors_origin):
  wait_until_ready()
  if cors_origin:
    check_cors(cors_origin)
    print('PASS: configured CORS origin accepted and unlisted origin rejected')
  _, live = expect('GET', '/actuator/health/liveness')
  check(live == {'status': 'UP'}, 'liveness must expose only UP status')
  expect('GET', '/actuator/env', status=404)
  expect('POST', API + '/tools/seal', {'plaintext': 'demo'}, status=403)

  email = 'compose-' + secrets.token_hex(12) + '@example.com'
  password = secrets.token_urlsafe(32) + 'Aa1!'
  headers, registered = expect('POST', API + '/auth/register', {'email': email, 'password': password})
  check(headers.get('Cache-Control') == 'no-store', 'registration must be no-store')
  token = registered['token']
  headers, logged_in = expect('POST', API + '/auth/login', {'email': email, 'password': password})
  check(headers.get('Cache-Control') == 'no-store', 'login must be no-store')
  login_token = logged_in['token']
  _, me = expect('POST', API + '/auth/me', token=login_token)
  check(me['userId'] == registered['userId'], 'login must authenticate the registered account')

  # Both failures exercise native password work; never inspect or print bodies.
  wrong_candidate = secrets.token_urlsafe(32)
  expect('POST', API + '/auth/login', {'email': email, 'password': wrong_candidate}, status=401)
  expect('POST', API + '/auth/login', {
    'email': 'missing-' + secrets.token_hex(12) + '@example.invalid', 'password': wrong_candidate,
  }, status=401)
  print('PASS: known-wrong and unknown-account logins both return generic 401')

  _, item = expect('POST', API + '/vault/items', {'label': 'Compose smoke item', 'generate': True}, token)
  item_path = API + '/vault/items/' + item['id']
  _, items = expect('GET', API + '/vault/items', token=token)
  check(len(items) == 1 and items[0]['id'] == item['id'], 'created item must appear in owner list')
  check('password' not in items[0] and 'envelopeBytes' not in items[0], 'list must omit secret fields')

  headers, overview = expect('GET', API + '/vault/overview', token=token)
  check(headers.get('Cache-Control') == 'no-store', 'overview must be no-store')
  check(overview['counts']['total'] == 1 and overview['counts']['unset'] == 1, 'overview must count owner metadata')
  check(overview['nextExpiry'] is None and len(overview['recent']) == 1, 'overview must keep missing expiry explicit')
  headers, page = expect('POST', API + '/vault/items/search', {'query': 'COMPOSE', 'field': 'label', 'sort': 'expiry'}, token)
  check(headers.get('Cache-Control') == 'no-store', 'search must be no-store')
  check(page['totalMatches'] == 1 and page['items'][0]['id'] == item['id'], 'bounded search must return owner metadata')
  check('password' not in page['items'][0] and 'envelopeBytes' not in page['items'][0], 'search must omit secret fields')
  _, literal = expect('POST', API + '/vault/items/search', {'query': '%', 'field': 'label'}, token)
  check(literal['totalMatches'] == 0, 'search wildcards must be literal')
  expect('POST', API + '/vault/items/search', {'size': 51}, token, status=400)
  expect('GET', API + '/vault/overview', status=401)
  expect('POST', API + '/vault/items/search', {}, status=401)

  headers, revealed = expect('POST', item_path + '/reveal', token=token)
  check('no-store' in headers.get('Cache-Control', '').lower(), 'reveal must be no-store')
  check(headers.get('Pragma') == 'no-cache' and headers.get('Expires') == '0', 'reveal cache compatibility headers')
  check(isinstance(revealed['password'], str) and len(revealed['password']) > 0, 'generated secret must be readable')
  expect('GET', item_path + '/reveal', token=token, status=405)

  selection = {'destination': 'keynest', 'scope': 'selected', 'itemIds': [item['id']]}
  export_headers, preview = expect('POST', API + '/vault/exports/preview', selection, token)
  check(export_headers.get('Cache-Control') == 'no-store' and preview['itemCount'] == 1, 'export preview scope/cache boundary')
  export_request = dict(selection, snapshot=preview['snapshot'], accountPassword=password,
    backupPassphrase=secrets.token_urlsafe(32))
  export_status, export_headers, protected = request('POST', API + '/vault/exports/download', export_request, token, binary=True)
  check(export_status == 200 and protected[:10] == b'KEYNESTB\x01\x01', 'encrypted export container framing')
  check(export_headers.get('Cache-Control') == 'no-store', 'encrypted export must be no-store')
  check(revealed['password'].encode('utf-8') not in protected, 'export must not expose plaintext')
  check(int.from_bytes(protected[26:30], 'big') == len(protected) - 30, 'export container length must match')

  other_password = secrets.token_urlsafe(32) + 'Aa1!'
  _, other = expect('POST', API + '/auth/register', {
    'email': 'compose-' + secrets.token_hex(12) + '@example.com',
    'password': other_password,
  })
  _, other_items = expect('GET', API + '/vault/items', token=other['token'])
  check(other_items == [], 'other user must have an empty vault')
  _, other_overview = expect('GET', API + '/vault/overview', token=other['token'])
  check(other_overview['counts']['total'] == 0, 'overview must isolate owners')
  _, other_page = expect('POST', API + '/vault/items/search', {'itemId': item['id']}, other['token'])
  check(other_page['totalMatches'] == 0 and other_page['items'] == [], 'selected-item search must isolate owners')
  expect('POST', API + '/vault/exports/preview', selection, other['token'], status=404)
  expect('GET', item_path, token=other['token'], status=404)
  expect('POST', item_path + '/reveal', token=other['token'], status=404)
  expect('PUT', item_path, {'label': 'Unauthorized edit'}, other['token'], status=404)
  expect('DELETE', item_path, token=other['token'], status=404)

  restore_request = {'file': base64.b64encode(protected).decode('ascii'),
    'backupPassphrase': export_request['backupPassphrase']}
  headers, restore_preview = expect('POST', API + '/vault/restores/preview', restore_request, other['token'])
  check(headers.get('Cache-Control') == 'no-store' and restore_preview['itemCount'] == 1,
    'restore preview scope/cache boundary')
  _, before_restore = expect('GET', API + '/vault/items', token=other['token'])
  check(before_restore == [], 'restore preview must not write')
  restore_request.update(accountPassword=other_password, planId=restore_preview['planId'])
  headers, restored = expect('POST', API + '/vault/restores/apply', restore_request, other['token'])
  check(headers.get('Cache-Control') == 'no-store' and restored['restoredCount'] == 1,
    'restore apply count/cache boundary')
  expect('POST', API + '/vault/restores/apply', restore_request, other['token'], status=409)
  repeat_body = {'file': restore_request['file'], 'backupPassphrase': restore_request['backupPassphrase']}
  _, repeated = expect('POST', API + '/vault/restores/preview', repeat_body, other['token'])
  check(repeated['newItemCount'] == 0 and repeated['skippedCount'] == 1, 'repeat preview must identify identical entries')
  repeat_body.update(accountPassword=other_password, planId=repeated['planId'])
  _, skipped = expect('POST', API + '/vault/restores/apply', repeat_body, other['token'])
  check(skipped['restoredCount'] == 0 and skipped['skippedCount'] == 1, 'repeat restore must not create duplicates')
  _, restored_items = expect('GET', API + '/vault/items', token=other['token'])
  check(len(restored_items) == 1 and restored_items[0]['id'] != item['id'], 'restore must create one fresh owner identity')
  restored_path = API + '/vault/items/' + restored_items[0]['id']
  _, restored_secret = expect('POST', restored_path + '/reveal', token=other['token'])
  check(restored_secret['password'] == revealed['password'], 'restore must preserve the password')
  expect('PUT', restored_path, {'password': 'aB2#mangoC3$deF4%'}, other['token'])
  selected_review = {'file': restore_request['file'], 'backupPassphrase': restore_request['backupPassphrase'],
    'decisions': [{'sourceIndex': 0, 'action': 'update', 'targetId': restored_items[0]['id']}]}
  _, selected = expect('POST', API + '/vault/restores/preview', selected_review, other['token'])
  check(selected['newItemCount'] == 0 and selected['updatedItemCount'] == 1, 'review must bind the selected replacement')
  replacement = {'file': restore_request['file'], 'backupPassphrase': restore_request['backupPassphrase'],
    'accountPassword': other_password, 'planId': selected['planId']}
  expect('POST', API + '/vault/restores/apply', replacement, other['token'], status=400)
  _, selected = expect('POST', API + '/vault/restores/preview', selected_review, other['token'])
  replacement.update(planId=selected['planId'], confirmUpdates=True)
  _, replaced = expect('POST', API + '/vault/restores/apply', replacement, other['token'])
  check(replaced['restoredCount'] == 0 and replaced['updatedCount'] == 1, 'confirmed restore must update without adding an identity')
  _, replaced_secret = expect('POST', restored_path + '/reveal', token=other['token'])
  check(replaced_secret['password'] == revealed['password'], 'confirmed replacement must restore the exact reviewed password')
  _, after_replace = expect('GET', API + '/vault/items', token=other['token'])
  check(len(after_replace) == 1 and after_replace[0]['id'] == restored_items[0]['id'], 'replacement must preserve the existing identity')
  expect('POST', restored_path + '/reveal', token=token, status=404)
  expect('DELETE', restored_path, token=other['token'])

  _, updated = expect('PUT', item_path, {'label': 'Updated smoke item'}, token)
  check(updated['label'] == 'Updated smoke item', 'owner must be able to update metadata')
  expect('POST', API + '/vault/exports/download', export_request, token, status=409)
  _, again = expect('POST', item_path + '/reveal', token=token)
  check(again['password'] == revealed['password'], 'metadata update must preserve the secret')
  expect('DELETE', item_path, token=token)
  _, remaining = expect('GET', API + '/vault/items', token=token)
  check(remaining == [], 'owner vault must be empty after deletion')

  expect('POST', API + '/auth/logout', token=login_token)
  expect('POST', API + '/auth/me', token=login_token, status=401)
  expect('POST', API + '/auth/logout', token=token)
  expect('POST', API + '/auth/logout', token=other['token'])
  print('PASS: packaged API health, auth, vault CRUD, bounded metadata queries, encrypted export/restore, identical-entry skips, confirmed replacements, replay refusal, no-store and tenant isolation')


if __name__ == '__main__':
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('--disposable', action='store_true', required=True,
    help='confirm localhost:8080 belongs to a fresh disposable Compose project')
  parser.add_argument('--cors-origin', help='expected browser origin for a hosted Compose preflight')
  args = parser.parse_args()
  try:
    run(args.cors_origin)
  except SmokeFailure as error:
    print('FAIL: ' + str(error), file=sys.stderr)
    sys.exit(1)
  except Exception:
    # No traceback or request/response bodies in CI/support output.
    print('FAIL: unexpected smoke-test response or connection error (payload suppressed)', file=sys.stderr)
    sys.exit(1)
