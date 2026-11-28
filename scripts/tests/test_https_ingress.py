"""Disposable loopback TLS tests; never contact a real deployment or retain keys."""

from contextlib import redirect_stdout
from html import escape
import importlib.util
import io
import json
from pathlib import Path
import ssl
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('ingress', ROOT / 'scripts/check_https_ingress.py')
ingress = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ingress)
SENTINEL = 'PRIVATE_SYNTHETIC_SENTINEL'


def document_for(policy):
  return ('<html><head><meta http-equiv="Content-Security-Policy" content="'
    + escape(policy['document'], quote=True) + '"></head><body>Disposable fixture</body></html>').encode()


class IngressTest(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    cls.private = tempfile.TemporaryDirectory(prefix='keynest-ingress-test-')
    cls.directory = Path(cls.private.name)
    cls.cert = cls.directory / 'certificate.pem'
    key = cls.directory / 'private.pem'
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
      '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost',
      '-addext', 'basicConstraints=critical,CA:TRUE', '-keyout', str(key), '-out', str(cls.cert)],
      check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
    cls.state = {}

    class Handler(BaseHTTPRequestHandler):
      def log_message(self, *args):
        pass

      def do_GET(self):
        state = cls.state
        state['requests'].append((self.path, dict(self.headers)))
        if self.server is cls.http:
          self.send_response(state.get('redirect_status', 308))
          self.send_header('Location', state.get('location', cls.web if self.path == '/' else cls.api + '/tools/health'))
          self.end_headers()
          return
        if self.path == '/redirect-trap':
          state['traps'] += 1
        api = self.path.startswith('/api/v1/')
        self.send_response(state.get('status', 401 if self.path.endswith('/vault/items') else 200))
        headers = {**ingress.BASE_HEADERS,
          'Content-Security-Policy': ingress.API_CSP if api else state.get('web_csp', ingress.WEB_CSP),
          'Strict-Transport-Security': state.get('hsts', 'max-age=300'),
          'Content-Type': 'application/json' if api else 'text/html; charset=utf-8'}
        if self.path.endswith('/vault/items'):
          headers['Cache-Control'] = state.get('cache', 'no-store')
        if state.get('redirect'):
          headers['Location'] = cls.web + 'redirect-trap'
        for name, value in headers.items():
          if name != state.get('missing'):
            self.send_header(name, value)
          if name == state.get('duplicate'):
            self.send_header(name, value)
        # A Set-Cookie must never become a subsequent Cookie request.
        self.send_header('Set-Cookie', 'synthetic=' + SENTINEL)
        self.end_headers()
        body = b'{}' if api else state.get('html', cls.html)
        if state.get('large'):
          body = b'x' * (ingress.MAX_HTML + 1)
        try:
          if state.get('slow'):
            time.sleep(0.15)
          self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ssl.SSLError):
          pass

    cls.tls = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(cls.cert, key)
    cls.tls.socket = context.wrap_socket(cls.tls.socket, server_side=True)
    cls.web = 'https://localhost:' + str(cls.tls.server_port) + '/'
    cls.api = cls.web.rstrip('/') + '/api/v1'
    cls.http = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    cls.http_url = 'http://localhost:' + str(cls.http.server_port)
    cls.threads = []
    for server in (cls.tls, cls.http):
      thread = threading.Thread(target=lambda s=server: s.serve_forever(poll_interval=0.01), daemon=True)
      thread.start()
      cls.threads.append(thread)
    # Use the real production policy builder, so successful fixtures match its contract.
    built = subprocess.run(['node', '--input-type=module', '-e',
      "import {securityPolicy} from './apps/manager-web/scripts/web-security.mjs'; console.log(JSON.stringify({absolute:securityPolicy(process.argv[1]),relative:securityPolicy()}))", cls.api],
      cwd=ROOT, check=True, capture_output=True, text=True, timeout=10)
    policies = json.loads(built.stdout)
    cls.policy = policies['absolute']
    cls.relative_policy = policies['relative']
    cls.html = document_for(cls.policy)

  @classmethod
  def tearDownClass(cls):
    for server in (cls.tls, cls.http):
      server.shutdown()
      server.server_close()
    for thread in cls.threads:
      thread.join(timeout=2)
    cls.private.cleanup()

  def setUp(self):
    self.state.clear()
    self.state.update(requests=[], traps=0)
    (self.directory / 'web-security.json').write_text(json.dumps(self.policy), encoding='utf-8')
    (self.directory / 'index.html').write_bytes(self.html)

  def args(self):
    return ['--probe', '--web-url', self.web, '--api-url', self.api, '--web-build', str(self.directory),
      '--revision', 'a' * 40, '--ca-file', str(self.cert)]

  def execute(self, args=None):
    output = io.StringIO()
    with redirect_stdout(output):
      code = ingress.main(self.args() if args is None else args)
    self.assertNotIn(SENTINEL, output.getvalue())
    return code, json.loads(output.getvalue())

  def test_real_tls_root_deep_link_health_denial_and_exact_redirects(self):
    args = self.args() + ['--web-http-url', self.http_url + '/', '--api-http-url', self.http_url + '/api/v1/tools/health']
    code, report = self.execute(args)
    self.assertEqual(code, 0)
    self.assertEqual(len(report['checks']), 6)
    self.assertTrue(all(check['result'] == 'PASS' for check in report['checks']))
    self.assertEqual([path for path, _ in self.state['requests']], ['/', '/vault', '/api/v1/tools/health', '/api/v1/vault/items', '/', '/api/v1/tools/health'])
    for _, headers in self.state['requests']:
      self.assertFalse(set(map(str.lower, headers)) & {'cookie', 'authorization', 'proxy-authorization'})

  def test_complete_production_response_policy_also_passes(self):
    self.state['web_csp'] = self.policy['responseHeaders']['Content-Security-Policy']
    self.assertEqual(self.execute()[0], 0)

  def test_same_origin_relative_build_is_bound_to_the_supplied_api(self):
    (self.directory / 'web-security.json').write_text(json.dumps(self.relative_policy), encoding='utf-8')
    self.state['html'] = document_for(self.relative_policy)
    (self.directory / 'index.html').write_bytes(self.state['html'])
    self.assertEqual(self.execute()[0], 0)
    args = self.args()
    args[args.index('--api-url') + 1] = 'https://other.example.invalid/api/v1'
    with patch.object(ingress, 'connection') as connect:
      self.assertEqual(self.execute(args)[1]['category'], 'BUILD_DESTINATION')
      connect.assert_not_called()

  def test_untrusted_and_wrong_hostname_certificates_fail(self):
    args = self.args()
    self.assertEqual(self.execute(args[:-2])[0], 1)
    args[args.index('--web-url') + 1] = self.web.replace('localhost', '127.0.0.1')
    # Keep artifact/API binding valid while testing the Web certificate name.
    self.assertEqual(self.execute(args)[1]['checks'][0]['category'], 'TRANSPORT')

  def test_missing_and_duplicate_headers_are_rejected(self):
    for fault in ('missing', 'duplicate'):
      for name in (*ingress.BASE_HEADERS, 'Content-Security-Policy', 'Strict-Transport-Security', 'Content-Type', 'Cache-Control'):
        with self.subTest(fault=fault, name=name):
          self.state.pop('missing', None)
          self.state.pop('duplicate', None)
          self.state[fault] = name
          self.assertEqual(self.execute()[0], 1)

  def test_disabled_malformed_or_duplicate_hsts_is_rejected(self):
    for value in ('max-age=0', 'max-age=-1', 'includeSubDomains', 'max-age=300; max-age=400',
      'max-age=300, max-age=400', 'max-age=300; includeSubDomains=bad'):
      with self.subTest(value=value):
        self.state['hsts'] = value
        self.assertEqual(self.execute()[0], 1)
    self.state['hsts'] = 'max-age="300"; includeSubDomains; preload'
    self.assertEqual(self.execute()[0], 0)

  def test_changed_document_response_and_cache_policies_are_rejected(self):
    self.state['html'] = self.html.replace(b'default-src &#x27;none&#x27;', b'default-src *')
    self.assertEqual(self.execute()[0], 1)
    self.state.pop('html')
    self.state['web_csp'] = "frame-ancestors *"
    self.assertEqual(self.execute()[0], 1)
    self.state.pop('web_csp')
    self.state['cache'] = 'public, max-age=3600'
    self.assertEqual(self.execute()[1]['checks'][-1]['category'], 'CACHE_POLICY')

  def test_html_body_and_network_wait_are_bounded(self):
    self.state['large'] = True
    self.assertEqual(self.execute()[1]['checks'][0]['category'], 'BODY_LIMIT')
    self.state.pop('large')
    self.state['slow'] = True
    code, report = self.execute(self.args() + ['--timeout', '0.05'])
    self.assertEqual(code, 1)
    self.assertIn(report['checks'][0]['category'], ('TRANSPORT', 'BODY_TIMEOUT'))

  def test_https_redirect_is_not_followed_and_remote_payload_is_suppressed(self):
    self.state.update(status=302, redirect=True, html=SENTINEL.encode())
    self.assertEqual(self.execute()[0], 1)
    self.assertEqual(self.state['traps'], 0)

  def test_http_redirect_must_match_exact_explicit_https_destination(self):
    self.state['location'] = self.web + 'redirect-trap?' + SENTINEL
    args = self.args() + ['--web-http-url', self.http_url + '/']
    self.assertEqual(self.execute(args)[1]['checks'][-1]['category'], 'REDIRECT_DESTINATION')
    self.assertEqual(self.state['traps'], 0)

  def test_ambient_proxies_and_cookies_are_never_used(self):
    with patch.dict('os.environ', {'https_proxy': 'http://invalid.example.invalid:9', 'http_proxy': 'http://invalid.example.invalid:9', 'no_proxy': ''}):
      self.assertEqual(self.execute()[0], 0)
    self.assertTrue(all('Cookie' not in headers for _, headers in self.state['requests']))

  def test_invalid_targets_and_arguments_fail_before_connections_without_echo(self):
    for value in ('http://localhost/', 'https://user:' + SENTINEL + '@localhost/',
      'https://localhost/?' + SENTINEL, 'https://localhost/#' + SENTINEL, 'https://localhost/extra',
      'https://localhost/%0a', 'https://localhost:\\evil/', 'https://localhost:/', 'https://localhost:0/',
      'https://localhost/\x00'):
      with self.subTest(value=value), patch.object(ingress, 'connection') as connect:
        args = self.args()
        args[args.index('--web-url') + 1] = value
        self.assertEqual(self.execute(args)[0], 1)
        connect.assert_not_called()
    self.assertEqual(self.execute(['--probe', '--unrecognized', SENTINEL])[1]['category'], 'ARGUMENTS')
    self.assertEqual(self.execute(self.args()[1:])[1]['category'], 'ARGUMENTS')

  def test_invalid_timeouts_revision_and_unexpected_exceptions_are_sanitized(self):
    for timeout in ('nan', 'inf', '0', '-1', '11', SENTINEL):
      self.assertEqual(self.execute(self.args() + ['--timeout', timeout])[0], 1)
    self.assertEqual(self.execute(self.args() + ['--revision', SENTINEL])[1]['category'], 'REVISION')
    with patch.object(ingress, 'connection', side_effect=RuntimeError(SENTINEL)):
      self.assertEqual(self.execute()[1]['category'], 'UNAVAILABLE')

  def test_wrong_local_build_and_duplicate_or_late_meta_fail_before_network(self):
    for body in (self.html.replace(b'</head>', b'<meta http-equiv="Content-Security-Policy" content="extra"></head>'),
      self.html.replace(b'<head>', b'<head><script src="/main.js"></script>')):
      with patch.object(ingress, 'connection') as connect:
        (self.directory / 'index.html').write_bytes(body)
        self.assertEqual(self.execute()[1]['category'], 'DOCUMENT_POLICY')
        connect.assert_not_called()
    (self.directory / 'index.html').write_bytes(self.html)
    policy = {**self.policy, 'apiBaseUrl': 'https://other.example.invalid/api/v1'}
    (self.directory / 'web-security.json').write_text(json.dumps(policy), encoding='utf-8')
    with patch.object(ingress, 'connection') as connect:
      self.assertEqual(self.execute()[1]['category'], 'BUILD_DESTINATION')
      connect.assert_not_called()


if __name__ == '__main__':
  unittest.main()
