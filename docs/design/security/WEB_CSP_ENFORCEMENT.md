# Production Web CSP delivery

H1 / #278; implementation tracked by #286. This document describes the proposed implementation, not a public deployment certificate or a cookie migration.

## Build and delivery contract

Use `npm run build` in Manager Web (or its existing Vercel wrapper). After a successful production Angular build, the wrapper inserts an enforcing CSP meta element before base/links/scripts and writes `web-security.json` alongside `index.html`. Both derive from the same validated `KEYNEST_API_BASE_URL` compiled into the application. The policy permits connections to self and only that explicit API origin; it has no HTTPS-wide/wildcard connection grant, script inline/eval exception or reporting egress. Existing Google Fonts origins and the reviewed style-only inline exception remain explicit limits. This is defense in depth; T02 is not eliminated.

Meta CSP does not enforce framing. Both supported `vercel.json` roots therefore supply `frame-ancestors 'none'`, `X-Frame-Options: DENY`, `nosniff` and `no-referrer` as response headers on all routes. The header and meta policies restrict together; neither may be omitted. Existing install/build commands, output directories and SPA rewrites remain. The Build Output API preparation script validates the generated artifact and emits the complete response-header policy before filesystem/SPA routing. It does not substitute arbitrary policy text from operator input.

Explicit development builds/`ng serve` remain outside this production-build contract. Production output-path overrides are honored. Directly invoking Angular CLI instead of the supported build wrapper bypasses policy generation and is not a verified production path. Do not add another policy to source HTML: generation rejects pre-existing CSP meta rather than silently overriding it. Deliver the complete built artifact; do not edit policy metadata by hand or mix HTML from one build with headers from another API configuration.

Production CSP requires a DNS API hostname (including localhost), a supported loopback IPv4 origin (`127.0.0.1`), or a same-origin proxy. Literal IPv6/non-loopback IPv4, wildcard, underscore and noncanonical/trailing-dot API hosts are rejected for this CSP delivery path rather than emitting a source expression that may not match. General API URL validation is unchanged; use a DNS name or relative API path when upgrading such a deployment. A relative API path still works when the document itself is served from an IP address. No broad source exemption is added to work around CSP host-source limitations.

## Portable proxy

The sample below serves the generated Web bundle and preserves the API's independent headers/cache controls. Replace the hostname/root, configure the exact Web origin in the existing API CORS allowlist, and review trusted-proxy configuration. A same-origin proxy does not waive backend origin checks. This is an operator template; no real proxy or public TLS host has been certified by these source tests.

```caddyfile
keynest.example.invalid {
  @api path /api/v1 /api/v1/*
  handle @api {
    reverse_proxy 127.0.0.1:8080
  }
  handle {
    root * /srv/keynest/browser
    header {
      Content-Security-Policy "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'"
      X-Frame-Options DENY
      X-Content-Type-Options nosniff
      Referrer-Policy no-referrer
    }
    try_files {path} /index.html
    file_server
  }
}
```

An ingress may instead use the full validated `responseHeaders` from the build's `web-security.json`. Keep the HTML policy too; additional CSP headers can only restrict, so a stale or incompatible header can break API access. Do not replace the API's `no-store`, CORS, download or security headers with Web defaults. Verify emitted headers on both document and SPA deep-link responses through the actual ingress before promoting. See [HTTPS/HSTS responsibilities](../../DEPLOYMENT.md#api-response-headers-and-https-ingress): application rollback does not erase browser-retained HSTS. Platform preview tooling/injected scripts are not granted production script permissions automatically.

## Evidence and remaining gates

Node tests cover policy generation, unsafe destinations, tampering, insertion order, both Vercel roots and output overrides. Browser fixtures now read the emitted policy/meta/header contract, not a test-only copy. Framing tests include a same-origin allow control and a blocked application document. A separate production loopback-API build supports actual browser requests from `http://localhost:4200` to `http://localhost:8080`, with API forwarding disabled in that fixture; normal and reduced-motion sessions must pass. The existing same-origin proxy runs and all public negative probes remain.

Record enforcement source/run results on #286. The follow-up fixture in #294 adds rotation/edit/delete and actual native download/file-selection/review/apply/duplicate-ignore/reveal; record its exact-source runtime result before claiming those journeys pass. This restores a deleted item within the same disposable account/instance, not across instances. Public ingress behavior and review of the transport amendment remain H1 acceptance work. No public host, Windows/Edge/Firefox matrix, conflict-update/keep-both/ambiguous-target CSP coverage or HttpOnly transport is claimed by these fixtures. Keep H1 open while remaining gates remain.

Rollback restores the previous complete Web build and matching host header configuration, removing generated meta/full-policy output as appropriate. It reopens the documented Web hardening gap and must not change data, keys or sessions. Do not weaken policy with wildcards to disguise a mismatched deployment.

## Primary references

- [CSP enforcement and intersecting policies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy)
- [CSP host-source grammar/matching](https://www.w3.org/TR/CSP3/)
- [Vercel configuration](https://vercel.com/docs/project-configuration)
- [Build Output API routing](https://vercel.com/docs/build-output-api/configuration)
