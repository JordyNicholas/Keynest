# Verify an operator-controlled HTTPS ingress

H1 #278 still needs evidence from each actual deployment. The opt-in checker in
[#333](https://github.com/JordyNicholas/Keynest/issues/333) compares selected responses
with the reviewed Web/API contract. Running its tests only verifies disposable
loopback fixtures; it does not verify your host or close H1.

## Prerequisites and invocation

Use Python 3.10 or later, a direct network route to your own endpoints, and the
trusted **production browser artifact from the candidate being deployed**. That
directory must contain `index.html` and its generated `web-security.json`; build
it using the supported [Web build wrapper](web-deployment.md). Development-server
output is outside this contract. The declared revision is an operator assertion,
not proof that a remote server or local artifact was built from that revision.

From the repository root, replace the example URLs, artifact directory and SHA:

```sh
python3 scripts/check_https_ingress.py --probe --web-url https://keynest-test.example.com/ --api-url https://keynest-api-test.example.com/api/v1 --web-build apps/manager-web/dist/manager-web/browser --revision YOUR_40_CHARACTER_COMMIT_SHA
```

Use `python` instead of `python3` if that is your Python launcher on Windows. No
shell-specific variable syntax is required. For a same-origin API, supply the
same Web origin followed by `/api/v1`; the local build must use that same-origin
destination. Subpath-hosted Web applications and custom API base paths are not
covered by this first checker.

For an installation using a private CA, add `--ca-file /private/path/ca.pem`.
Certificate and hostname verification remain enabled. There is no insecure-TLS
option. A normal run uses the platform/Python trust store.

HTTP redirect checks are optional and require explicit source URLs:

```sh
python3 scripts/check_https_ingress.py --probe --web-url https://keynest-test.example.com/ --api-url https://keynest-api-test.example.com/api/v1 --web-build apps/manager-web/dist/manager-web/browser --revision YOUR_40_CHARACTER_COMMIT_SHA --web-http-url http://keynest-test.example.com/ --api-http-url http://keynest-api-test.example.com/api/v1/tools/health
```

The redirect must name the exact corresponding HTTPS destination. It is inspected
without following it. If your operator-approved setup intentionally redirects to
another canonical hostname, use that hostname as the deployment target and review
the alternate-host routing separately; this tool does not follow a chain.

Only `--probe` opts into connections. Targets reject credentials, query strings,
fragments and unexpected paths before any connection. Proxy environment variables
are ignored; the tool never reads browser sessions or supplies Authorization,
cookies, API keys, passwords or request bodies. It makes one GET per selected
check, with no retries. Do not put secrets in its arguments.

## Checks and output

| Check | Expected response |
| --- | --- |
| `WEB_ROOT` | HTTPS `/`, HTTP 200, HTML matching the trusted local index byte-for-byte, one reviewed response CSP and the matching generated document meta policy before resources |
| `WEB_DEEP_LINK` | HTTPS `/vault` serves the same policy contract as the root, without requiring an authenticated API call |
| `API_HEALTH` | HTTPS `/api/v1/tools/health`, HTTP 200, JSON content type and exact API baseline headers |
| `API_UNAUTHENTICATED` | HTTPS `/api/v1/vault/items`, HTTP 401, JSON content type, no-store and exact API baseline headers |
| `WEB_HTTP_REDIRECT`, when requested | Supplied HTTP root returns one redirect Location matching the HTTPS Web root |
| `API_HTTP_REDIRECT`, when requested | Supplied HTTP health URL returns one redirect Location matching the HTTPS API health URL |

All four HTTPS checks require exactly one `nosniff`, `no-referrer`, `DENY` and
positive `max-age` HSTS header. Duplicate baseline/CSP/HSTS headers fail. The Web
response CSP must be either the reviewed framing policy or the complete response
policy generated in the trusted local artifact. Meta policy placement/content and
the configured API destination must match. Root and deep-link HTML must match the
trusted local index byte-for-byte; proxy HTML rewriting/minification or injected
preview scripts are outside this first exact-artifact check. These are conservative,
exact-contract comparisons: an intentionally different delivery contract requires
review, not a bypass flag. The metadata parser is not a full HTML5/browser validator;
the trusted local artifact and real browser evidence remain prerequisites.

Exit code 0 means all requested checks passed; exit code 1 means a failure. Output
is one JSON object with a timestamp, declared candidate SHA and fixed check/result
categories. Invalid arguments and unexpected exceptions are sanitized too. It
never prints remote URLs, header values, response bodies or raw exception text.
The report does not retain host identity: record the operator-controlled hostname,
artifact provenance and deployment identifier separately in your private evidence.

`--timeout` defaults to five seconds and accepts values above zero up to ten.
Web HTML is limited to 2 MiB; local policy metadata is limited to 64 KiB. Body reads
check an elapsed-time budget between socket reads. DNS resolution and an active
socket operation depend on platform timing, so this is not an absolute process
deadline. Compressed HTML is refused; requests explicitly ask for identity encoding.

## Interpret the evidence and retain the remaining gates

A pass establishes only the observed response contract at that time. It does not
run Angular, verify browser framing enforcement, test credentialed CORS/CSRF,
demonstrate cookie transport, prove authenticated proxy/error/download paths,
validate resolved client identity, certify every edge location, or prove artifact
or application identity. HTTP JSON bodies are not interpreted as proof of health.
Keep the [real browser fixture](../security/controls/web-csp-verification.md) and
the operator's disposable-account walkthrough alongside this result.

HSTS syntax and a positive lifetime are checked; `includeSubDomains`, preload
eligibility and retention are **not approved by a pass**. Review all affected DNS
names and rollback consequences before widening HSTS. Browsers do not treat IP
addresses as HSTS hosts; literal-IP checks can exercise certificates/headers but
are not browser HSTS evidence. See [HTTPS/HSTS responsibilities](deployment.md#api-response-headers-and-https-ingress).

For each candidate, retain the source/run and trusted build provenance, private
deployment identity, UTC observation time, sanitized report, exact browser/OS and
manual outcomes. Keep #278 open until its remaining transport, deployment and
security-acceptance gates are met. #268 still tracks supplementary platform,
upgrade, cross-instance and post-commit lost-response evidence. #328 records the
historical intermittent CI observation and the maintainer's subsequent successful
manual walkthrough; a passing ingress check does not establish its cause.

The command changes no server configuration or data and needs no application
rollback. Reverting the checker and its CI test step removes this verification
aid; it does not alter existing Web/API headers, sessions, keys or release gates.
No public hosting, mail/provider traffic, publication or release is introduced.

## Test the checker locally

```sh
python3 -m unittest discover -s scripts/tests -p test_https_ingress.py
```

Tests need Node (to use the real Web policy builder) and OpenSSL (to create a fresh
temporary certificate/key). They bind only loopback, exercise certificate/name
rejection, header/CSP/cache failures, bounds, redirects and output sanitization,
then remove their temporary keys. CI runs these tests in the existing packaged
Compose job without changing its seven application check names or publishing.

## Primary references

- [Python urllib handlers and disabling ambient proxies](https://docs.python.org/3/library/urllib.request.html)
- [RFC 6797: HSTS syntax and processing](https://www.rfc-editor.org/rfc/rfc6797.html)
- [MDN: HSTS behavior and deployment considerations](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Strict-Transport-Security)
