# M1 H1 — browser and API response-header design

Status: proposed, November 26, 2026. Issue #278. This starts the updated roadmap's first hardening item; it does not enable headers, change sessions, approve an ADR amendment or establish deployed-host behavior.

## Verified source gap

Reviewed main `e007c1bfda7c7071c1c436649084605f9dd88417`, including the November 25 roadmap, idea catalog and Vercel default-policy build fix. Both `vercel.json` and `apps/manager-web/vercel.json` have zero explicit header routes. Angular build options have no explicit security/CSP option; `src/index.html` has no nonce marker. Export/restore filters already add `nosniff` and no-store for those routes, but no shared API baseline-header filter was found. These are source observations, not evidence that a particular hosted response lacks provider-injected headers or that XSS was demonstrated. No deployed endpoint was probed.

Reproduce by parsing both Vercel JSON files' `headers`, inspecting Angular build options/index, and searching Manager filters for `setHeader`, `Content-Security-Policy`, `X-Frame-Options`, `Referrer-Policy` and HSTS. Preserve existing cache/CORS/rate-limit behavior and the newly fixed packaged default-policy path.

## Proposed implementation slices

| Slice | Proposed control | Required evidence before enforcement |
| --- | --- | --- |
| API baseline | Shared filter supplies `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` and framing denial on API responses, including rejection/error/preflight paths | MockMvc/real HTTP success, 400/401/404/429/503 and CORS preflight; preserve content type, CORS and existing no-store responses |
| Static Web | Both supported Vercel roots and a portable proxy example use the same reviewed policy: no object embedding, no framing, constrained base/form destinations and self-hosted scripts | Production build served with actual headers; assert arbitrary inline/remote scripts and embedding are rejected while lazy loading and core flows work |
| Angular styles | Investigate a per-response nonce for Angular/Material style injection and critical styles, or explicitly review a narrowly limited style-only exception for static hosting | Actual Angular 21/Material dialogs, calendar, overlays, animation, pagination and reduced-motion coverage; no fixed reusable nonce or script `unsafe-inline`/`unsafe-eval` workaround |
| API destination | Bind `connect-src` to the validated build-time API origin plus self; keep provider requests server-side | Same-origin and explicitly configured split-origin builds; reject an unintended destination. Do not silently introduce `https:`/wildcard egress or break the supported API-base override |
| HTTPS ingress | Document operator-controlled HSTS on real HTTPS domains | Verify actual ingress and subdomain readiness; no unconditional preload/includeSubDomains claim, no HSTS based on untrusted forwarded headers, no localhost upgrade requirement |

Start with report-only validation in an isolated fixture where needed, then require a reviewed enforcing policy. Report-only is not an XSS mitigation and does not close H1. Do not add an external CSP reporting endpoint: reports can contain sensitive URLs or samples. Keep diagnostics local and sanitized. Do not add cross-origin-isolation/COEP blindly; assess downloads, clipboard, external references and split-origin behavior first.

The exact enforcing CSP string remains a design/compatibility decision. Static JSON headers alone cannot supply an unpredictable per-response nonce. Test Angular's documented nonce/CSP mechanisms and the pinned builder behavior before choosing between an ingress that injects matching nonces and a documented static-host style exception. Preserve strict script restrictions in either case. Provider preview tooling and development HMR require their own explicitly scoped test policy; do not weaken production to make a development harness pass.

## ADR-0007 amendment to prepare

Keep the currently accepted tab bearer transport while comparing an HttpOnly-cookie option. The amendment must specify same-origin versus split-origin topology, Secure/SameSite/domain/path, CSRF token and origin enforcement, exact credentialed CORS, session rotation/revocation, logout/expiry, local HTTP development, migration of existing tabs and rollback. HttpOnly reduces JavaScript token readout; it does not make an XSS-capable page unable to act as the user. No cookie transport switch is approved or implemented here.

## Verification matrix and rollout gate

Run anonymous homepage generator/checker and guarded deep links, sign-in/register/logout, account policy, Vault create/reveal/rotate/delete, calendar, export/download and restore review/apply under the policy. Verify blocked script/framing/unintended connection attempts independently of successful workflows. Add HTTP tests for baseline headers on all response classes and tests that proxy overrides cannot erase required protections. Repeat split-origin CORS and no-store checks; exercise keyboard/focus and reduced motion.

Record exact source, browser, fixture/proxy configuration, emitted headers and outcomes without real passwords/tokens or reporting egress. Public hosting and the supplementary maintainer checks in #268 remain separate. Rollout requires reviewed security-change approval plus passing browser/API/Compose evidence; a configuration-only diff or a successful build does not close the gate. Rollback restores the prior header configuration, explicitly reopening its documented risk; it must not alter data, keys or sessions. H1 remains open until implementation/negative tests and the transport amendment review are complete.

## Primary references

- [Angular security and CSP](https://angular.dev/best-practices/security#content-security-policy)
- [CSP directives and enforcement](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy)
- [HSTS scope and deployment behavior](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Strict-Transport-Security)
- [Vercel response security headers](https://vercel.com/docs/cdn-security/security-headers)
- [Accepted session transport ADR](../../ADR-0007-session-token-storage-and-browser-transport.md)
