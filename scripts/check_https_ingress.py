"""Opt-in, unauthenticated HTTPS contract checks; output never includes remote data."""

import argparse
from datetime import datetime, timezone
from html.parser import HTMLParser
from ipaddress import ip_address
import json
from pathlib import Path
import re
import ssl
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit, urlunsplit
from urllib.request import HTTPRedirectHandler, HTTPSHandler, ProxyHandler, Request, build_opener


MAX_HTML = 2 * 1024 * 1024
BASE_HEADERS = {'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer', 'X-Frame-Options': 'DENY'}
WEB_CSP = "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'"
API_CSP = "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"


class CheckFailure(Exception):
  """Only fixed internal categories may be used as messages."""


def require(condition, category):
  if not condition:
    raise CheckFailure(category)


def target(value, scheme='https', path='/'):
  """Accept a canonical, explicit target, never credentials, queries or fragments."""
  try:
    require(isinstance(value, str) and value.isascii()
      and all(33 <= ord(char) <= 126 for char in value) and not re.search(r'[\\%]', value), 'INVALID_TARGET')
    parsed = urlsplit(value)
    require(parsed.scheme == scheme and parsed.hostname and parsed.username is None and parsed.password is None
      and not parsed.query and not parsed.fragment and '?' not in value and '#' not in value
      and parsed.path in (('', '/') if path == '/' else (path,)), 'INVALID_TARGET')
    require(parsed.port is None or 1 <= parsed.port <= 65535, 'INVALID_TARGET')
    require(not parsed.netloc.endswith(':') and not parsed.hostname.endswith('.'), 'INVALID_TARGET')
    try:
      ip_address(parsed.hostname)
    except ValueError:
      require(len(parsed.hostname) <= 253 and all(re.fullmatch(r'[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?', label)
        for label in parsed.hostname.split('.')), 'INVALID_TARGET')
    return urlunsplit((scheme, parsed.netloc, path, '', ''))
  except (ValueError, TypeError):
    raise CheckFailure('INVALID_TARGET') from None


class DocumentPolicy(HTMLParser):
  def __init__(self):
    super().__init__(convert_charrefs=True)
    self.in_head = False
    self.heads = 0
    self.resources = False
    self.policies = []
    self.inert = []

  def handle_starttag(self, tag, attrs):
    if tag == 'head':
      self.heads += 1
      self.in_head = True
    if self.in_head and not self.inert and tag not in ('head', 'base', 'link', 'meta', 'title',
      'noscript', 'style', 'template', 'script'):
      # HTML body content implicitly ends the head, even without </head>.
      self.in_head = False
    if tag in ('template', 'noscript'):
      self.inert.append(tag)
    if tag in ('script', 'link', 'base'):
      self.resources = True
    if tag == 'meta':
      names = [key for key, _ in attrs]
      require(len(names) == len(set(names)), 'DOCUMENT_POLICY')
      values = dict(attrs)
      if (values.get('http-equiv') or '').lower() == 'content-security-policy':
        require(self.in_head and not self.resources and not self.inert, 'DOCUMENT_POLICY')
        self.policies.append(values.get('content'))

  def handle_endtag(self, tag):
    if tag in ('template', 'noscript'):
      require(self.inert and self.inert[-1] == tag, 'DOCUMENT_POLICY')
      self.inert.pop()
    if tag == 'head':
      self.in_head = False


def document_policy(body, expected):
  parser = DocumentPolicy()
  try:
    parser.feed(body.decode('utf-8'))
    parser.close()
  except UnicodeError:
    raise CheckFailure('DOCUMENT_POLICY') from None
  require(parser.heads == 1 and parser.policies == [expected], 'DOCUMENT_POLICY')


def read_local(path, limit):
  with path.open('rb') as stream:
    value = stream.read(limit + 1)
  require(len(value) <= limit, 'BUILD_LIMIT')
  return value


def load_policy(directory, api_url, web_url):
  policy = json.loads(read_local(directory / 'web-security.json', 65536))
  require(isinstance(policy, dict) and policy.get('schemaVersion') == 1, 'BUILD_POLICY')
  require(isinstance(policy.get('document'), str) and isinstance(policy.get('responseHeaders'), dict), 'BUILD_POLICY')
  configured = policy.get('apiBaseUrl')
  require(configured == '/api/v1' and api_url == web_url.rstrip('/') + '/api/v1'
    or configured == api_url, 'BUILD_DESTINATION')
  require(all(policy['responseHeaders'].get(name) == value for name, value in BASE_HEADERS.items()), 'BUILD_POLICY')
  require(policy['responseHeaders'].get('Content-Security-Policy') == policy['document'] + "; frame-ancestors 'none'", 'BUILD_POLICY')
  document_policy(read_local(directory / 'index.html', MAX_HTML), policy['document'])
  return policy


class NoRedirect(HTTPRedirectHandler):
  def redirect_request(self, req, fp, code, msg, headers, newurl):
    return None


def connection(ca_file=None):
  # Supplying a private CA preserves certificate and hostname verification.
  context = ssl.create_default_context(cafile=ca_file)
  return build_opener(ProxyHandler({}), NoRedirect(), HTTPSHandler(context=context))


def fetch(opener, url, timeout, html=False):
  request = Request(url, method='GET', headers={'Accept-Encoding': 'identity', 'User-Agent': 'Keynest-Ingress-Check/1'})
  try:
    try:
      response = opener.open(request, timeout=timeout)
    except HTTPError as error:
      response = error
    with response:
      body = bytearray()
      if html and response.status == 200:
        require(response.headers.get('Content-Encoding', 'identity').lower() == 'identity', 'CONTENT_ENCODING')
        deadline = time.monotonic() + timeout
        while True:
          require(time.monotonic() < deadline, 'BODY_TIMEOUT')
          chunk = response.read1(min(65536, MAX_HTML + 1 - len(body)))
          if not chunk:
            break
          body.extend(chunk)
          require(len(body) <= MAX_HTML, 'BODY_LIMIT')
      return response.status, response.headers, bytes(body)
  except (ssl.SSLError, URLError, TimeoutError, OSError):
    raise CheckFailure('TRANSPORT') from None


def single(headers, name):
  values = headers.get_all(name, [])
  require(len(values) == 1, 'HEADER_COUNT')
  return values[0]


def baseline(headers):
  for name, value in BASE_HEADERS.items():
    require(single(headers, name).lower() == value.lower(), 'BASELINE_HEADER')
  hsts = single(headers, 'Strict-Transport-Security')
  directives = [part.strip() for part in hsts.split(';') if part.strip()]
  names = [part.split('=', 1)[0].strip().lower() for part in directives]
  require(len(names) == len(set(names)) and all(re.fullmatch(r'[a-zA-Z0-9_-]+(?:\s*=\s*(?:[a-zA-Z0-9_-]+|"[a-zA-Z0-9_-]+"))?', part)
    for part in directives), 'HSTS')
  require(all(part != 'includesubdomains' or '=' not in directive
    for part, directive in zip(names, directives)), 'HSTS')
  ages = [part for part in directives if part.split('=', 1)[0].strip().lower() == 'max-age']
  require(len(ages) == 1, 'HSTS')
  match = re.fullmatch(r'max-age\s*=\s*(?:"([0-9]+)"|([0-9]+))', ages[0], re.I)
  require(match is not None and int(match.group(1) or match.group(2)) > 0, 'HSTS')
  # Broad HSTS scope is deliberately left for the operator's domain review.


def redirect(opener, source, destination, timeout):
  require(urlsplit(source).hostname == urlsplit(destination).hostname, 'REDIRECT_TARGET')
  status, headers, _ = fetch(opener, source, timeout)
  require(status in (301, 302, 303, 307, 308), 'REDIRECT_STATUS')
  require(urljoin(source, single(headers, 'Location')) == destination, 'REDIRECT_DESTINATION')


def run_checks(args):
  web = target(args.web_url)
  api = target(args.api_url, path='/api/v1')
  web_http = target(args.web_http_url, 'http') if args.web_http_url else None
  api_http = target(args.api_http_url, 'http', '/api/v1/tools/health') if args.api_http_url else None
  # Validate all inputs and the trusted local artifact before any connection.
  if web_http:
    require(urlsplit(web_http).hostname == urlsplit(web).hostname, 'REDIRECT_TARGET')
  if api_http:
    require(urlsplit(api_http).hostname == urlsplit(api).hostname, 'REDIRECT_TARGET')
  policy = load_policy(Path(args.web_build), api, web)
  opener = connection(args.ca_file)
  results = []

  def check(name, action):
    try:
      action()
      results.append({'check': name, 'result': 'PASS'})
    except CheckFailure as failure:
      results.append({'check': name, 'result': 'FAIL', 'category': str(failure)})
    except Exception:
      results.append({'check': name, 'result': 'FAIL', 'category': 'UNAVAILABLE'})

  def web_document(url):
    status, headers, body = fetch(opener, url, args.timeout, html=True)
    require(status == 200, 'STATUS')
    baseline(headers)
    require(single(headers, 'Content-Type').split(';', 1)[0].lower() == 'text/html', 'CONTENT_TYPE')
    require(single(headers, 'Content-Security-Policy') in (WEB_CSP, policy['responseHeaders']['Content-Security-Policy']), 'RESPONSE_POLICY')
    document_policy(body, policy['document'])

  def api_response(path, expected):
    status, headers, _ = fetch(opener, api + path, args.timeout)
    require(status == expected, 'STATUS')
    baseline(headers)
    require(single(headers, 'Content-Security-Policy') == API_CSP, 'RESPONSE_POLICY')
    require(single(headers, 'Content-Type').split(';', 1)[0].lower() == 'application/json', 'CONTENT_TYPE')
    if expected == 401:
      require('no-store' in [part.strip().lower() for part in single(headers, 'Cache-Control').split(',')], 'CACHE_POLICY')

  check('WEB_ROOT', lambda: web_document(web))
  check('WEB_DEEP_LINK', lambda: web_document(web + 'vault'))
  check('API_HEALTH', lambda: api_response('/tools/health', 200))
  check('API_UNAUTHENTICATED', lambda: api_response('/vault/items', 401))
  if web_http:
    check('WEB_HTTP_REDIRECT', lambda: redirect(opener, web_http, web, args.timeout))
  if api_http:
    check('API_HTTP_REDIRECT', lambda: redirect(opener, api_http, api + '/tools/health', args.timeout))
  return results


class SafeParser(argparse.ArgumentParser):
  def error(self, message):
    # argparse's default error can echo a supplied credential-bearing URL.
    raise CheckFailure('ARGUMENTS')


def main(argv=None):
  parser = SafeParser(description=__doc__)
  parser.add_argument('--probe', action='store_true', required=True, help='Explicitly contact the supplied operator-controlled endpoints')
  parser.add_argument('--web-url', required=True)
  parser.add_argument('--api-url', required=True)
  parser.add_argument('--web-build', required=True, help='Trusted local production browser artifact directory')
  parser.add_argument('--revision', required=True, help='Declared candidate Git SHA; not a remote provenance proof')
  parser.add_argument('--ca-file', help='Explicit private CA trust file; hostname checks remain enabled')
  parser.add_argument('--web-http-url', help='Optional exact HTTP root URL; redirect is checked without following it')
  parser.add_argument('--api-http-url', help='Optional exact HTTP /api/v1/tools/health URL')
  parser.add_argument('--timeout', type=float, default=5)
  try:
    args = parser.parse_args(argv)
    require(re.fullmatch('[0-9a-f]{40}', args.revision) is not None, 'REVISION')
    require(0 < args.timeout <= 10, 'TIMEOUT')
    results = run_checks(args)
    passed = all(result['result'] == 'PASS' for result in results)
    report = {'schemaVersion': 1, 'time': datetime.now(timezone.utc).isoformat(), 'declaredRevision': args.revision,
      'result': 'PASS' if passed else 'FAIL', 'checks': results}
  except CheckFailure as failure:
    report = {'schemaVersion': 1, 'result': 'FAIL', 'category': str(failure)}
    passed = False
  except Exception:
    report = {'schemaVersion': 1, 'result': 'FAIL', 'category': 'UNAVAILABLE'}
    passed = False
  print(json.dumps(report, separators=(',', ':')))
  return 0 if passed else 1


if __name__ == '__main__':
  sys.exit(main())
