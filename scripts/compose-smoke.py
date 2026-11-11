"""Exercise only the disposable local Compose API; never print secret payloads."""

import argparse
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


def request(method, path, body=None, token=None):
  headers = {'Content-Type': 'application/json'}
  if token:
    headers['Authorization'] = 'Bearer ' + token
  data = None if body is None else json.dumps(body).encode('utf-8')
  req = Request(BASE + path, data=data, headers=headers, method=method)
  try:
    with opener.open(req, timeout=5) as response:
      return response.status, response.headers, json.load(response)
  except HTTPError as error:
    # Error payloads can contain user data; callers need only the status.
    status = error.code
    response_headers = error.headers
    error.close()
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


def run():
  wait_until_ready()
  _, live = expect('GET', '/actuator/health/liveness')
  check(live == {'status': 'UP'}, 'liveness must expose only UP status')
  expect('GET', '/actuator/env', status=404)
  expect('POST', API + '/tools/seal', {'plaintext': 'demo'}, status=403)

  email = 'compose-' + secrets.token_hex(12) + '@example.com'
  password = secrets.token_urlsafe(32) + 'Aa1!'
  _, registered = expect('POST', API + '/auth/register', {'email': email, 'password': password})
  token = registered['token']
  _, logged_in = expect('POST', API + '/auth/login', {'email': email, 'password': password})
  login_token = logged_in['token']
  _, me = expect('POST', API + '/auth/me', token=login_token)
  check(me['userId'] == registered['userId'], 'login must authenticate the registered account')

  _, item = expect('POST', API + '/vault/items', {'label': 'Compose smoke item', 'generate': True}, token)
  item_path = API + '/vault/items/' + item['id']
  _, items = expect('GET', API + '/vault/items', token=token)
  check(len(items) == 1 and items[0]['id'] == item['id'], 'created item must appear in owner list')
  check('password' not in items[0] and 'envelopeBytes' not in items[0], 'list must omit secret fields')

  headers, revealed = expect('POST', item_path + '/reveal', token=token)
  check('no-store' in headers.get('Cache-Control', '').lower(), 'reveal must be no-store')
  check(headers.get('Pragma') == 'no-cache' and headers.get('Expires') == '0', 'reveal cache compatibility headers')
  check(isinstance(revealed['password'], str) and len(revealed['password']) > 0, 'generated secret must be readable')
  expect('GET', item_path + '/reveal', token=token, status=405)

  _, other = expect('POST', API + '/auth/register', {
    'email': 'compose-' + secrets.token_hex(12) + '@example.com',
    'password': secrets.token_urlsafe(32) + 'Aa1!',
  })
  _, other_items = expect('GET', API + '/vault/items', token=other['token'])
  check(other_items == [], 'other user must have an empty vault')
  expect('GET', item_path, token=other['token'], status=404)
  expect('POST', item_path + '/reveal', token=other['token'], status=404)
  expect('PUT', item_path, {'label': 'Unauthorized edit'}, other['token'], status=404)
  expect('DELETE', item_path, token=other['token'], status=404)

  _, updated = expect('PUT', item_path, {'label': 'Updated smoke item'}, token)
  check(updated['label'] == 'Updated smoke item', 'owner must be able to update metadata')
  _, again = expect('POST', item_path + '/reveal', token=token)
  check(again['password'] == revealed['password'], 'metadata update must preserve the secret')
  expect('DELETE', item_path, token=token)
  _, remaining = expect('GET', API + '/vault/items', token=token)
  check(remaining == [], 'owner vault must be empty after deletion')

  expect('POST', API + '/auth/logout', token=login_token)
  expect('POST', API + '/auth/me', token=login_token, status=401)
  expect('POST', API + '/auth/logout', token=token)
  expect('POST', API + '/auth/logout', token=other['token'])
  print('PASS: packaged API health, auth, vault CRUD, no-store reveal and tenant isolation')


if __name__ == '__main__':
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('--disposable', action='store_true', required=True,
    help='confirm localhost:8080 belongs to a fresh disposable Compose project')
  parser.parse_args()
  try:
    run()
  except SmokeFailure as error:
    print('FAIL: ' + str(error), file=sys.stderr)
    sys.exit(1)
  except Exception:
    # No traceback or request/response bodies in CI/support output.
    print('FAIL: unexpected smoke-test response or connection error (payload suppressed)', file=sys.stderr)
    sys.exit(1)
