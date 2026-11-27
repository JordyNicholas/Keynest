# Central API and handler authentication

Roadmap M1 H3 / #303. Current source previously depended on each controller calling `AuthService.requireUser`. This layer centralizes admission while retaining controller/service owner checks, fresh revocation callbacks and current bearer transport. It adds no dependency, session/key format or schema migration. Candidate evidence is recorded on the linked issue/PR, not inferred from test source.

## Two boundaries

The primary API gate is a servlet filter before MVC argument/header/body binding. It shares the catalog's compiled Spring path patterns: only explicit public routes bypass authentication, optional-bearer tools may use the default policy anonymously, and every other `/api` path requires one usable Authorization header. It retains the existing AuthService credential parsing and digest-only session lookup; missing/invalid/expired/revoked or duplicate headers refuse access with no-store. Unknown canonical API paths now return 401 instead of anonymous 404; authenticated unclassified handlers are also refused. Method-not-supported, invalid URI/content negotiation and other container/framework rejections may still happen before handler checks; none grants protected content.

After Spring selects a handler, a globally discovered `MappedInterceptor` verifies the exact HTTP method, canonical matched pattern, controller identity and method name against the explicit catalog. A new application handler, an unreviewed overload, or a different handler replacing a public mapping cannot inherit that grant. Application controllers outside `/api` are covered by this check too. A default catalog decision is never a new public permission. Its inventory test requires an exact one-to-one set of explicit method/path/handler decisions, with no parameter/header/custom-condition overloads. Moving/adding a controller requires catalog and test review.

The API filter is the primary boundary, consistent with Spring's warning about interceptor/path matching differences. The interceptor is an additional matched-handler check and handles application mappings outside the current API namespace. It uses Spring's actual selected pattern/handler, not another independent URI matcher. A MappedInterceptor bean is discovered by other framework HandlerMappings too; the MVC-only registration form is deliberately avoided.

The filter marks successful authentication only for its current dispatch, without caching a UserAccount or a durable permission. The handler gate avoids a redundant central lookup for that dispatch; it authenticates independently if reached without the filter. The marker is removed in finally, and the filter checks ASYNC dispatches again. Domain/controller checks still run and export/restore still recheck revocation/credentials at their existing points. There is one extra central session/user lookup compared with the earlier controller-only path; no measured production throughput claim is made.

## Explicit current surfaces

The catalog declares 27 method/path/handler decisions: eight public, three optional-bearer and sixteen authenticated.

| Treatment | Surfaces |
| --- | --- |
| Public | Register/login; idempotent logout; tool health; bounded hash/verify tools |
| Public subject to existing operator gate | Development seal/open; Docker/production gate still refuses them |
| Optional bearer | Policy, generate and validate: no header uses the default policy; a supplied header must be valid before using account policy |
| Authenticated | Identity lookup; Vault CRUD/reveal/search/overview; account policy; export/restore preview/apply; AI explanation |

GET-backed HEAD requests keep the same admission as their selected GET handler. The existing exact-origin/method/header configuration moves from late MVC handling to a Spring CorsFilter before authentication, with no credentialed CORS added. This single configuration source handles preflight without application execution and supplies the allowed origin on early 401s, so split-origin clients can observe expiry rather than a generic network failure. Unlisted origins still receive no grant. Public routes retain their existing handling of irrelevant Authorization headers. Protected/optional supplied credentials must be exactly one header; the earlier per-controller first-header behavior is tightened. Public logout remains an idempotent digest-revocation endpoint and grants no authentication.

Existing ordering is explicit: API baseline headers, fixed security outcomes, CORS, export/restore size/cache bounds, client/route rate admission, then central authentication. Thus bounded size/rate failures can still precede a 401 and preserve their existing status/headers. Account-level login throttling remains inside the public login use case. Known export/restore 401 responses preserve their fixed SESSION_REQUIRED codes. Other authentication refusals use a fixed generic body without raw credentials or account data.

## Scope and compatibility

Actuator/status/error handling outside the API keeps its existing independent configuration. Hidden Actuator endpoints do not become public. The servlet inventory pins the current application to DispatcherServlet; new servlets, early-response filters, custom HandlerMappings, WebSocket/agent transports or management contexts need separate security review and tests. This is not a universal guard for arbitrary code or transports that bypass the reviewed Servlet/MVC pipeline. No OIDC, email activation, action confirmation, cookie/CSRF migration or new public capabilities follow from this change.

Before promoting, verify direct and ingress-visible responses, exact CORS, proxy/context-path behavior and all tenant/revocation/bulk operations on the intended deployment. H1 ingress/transport and H4 session-lifecycle gates remain separate. Current controller authorization stays in place; this slice does not consolidate away redundant checks at the cost of a fresh-read guarantee.

Rollback removes central filter/interceptor registration/catalog, restores the prior missing-header/unknown-route expectations, and restores the former implicit client-filter ordering and late MVC CORS configuration. Controller/service authorization and rate/size/cache/security-event controls must remain. No stored passwords, sessions, keys or Vault rows are changed.

## Verification

The real HTTP context uses `/keynest` to verify application-relative matching. Tests enumerate the catalog, refuse protected malformed bodies/identifiers before binding, check absent/invalid/duplicate/revoked sessions, retain optional/public/demo/HEAD/CORS behavior, deny dynamically unclassified handlers inside/outside the API namespace even with valid credentials, and prevent an unexpected handler inheriting a public grant. A test-only classified handler with no local authentication call must remain inaccessible anonymously and run with a valid session. Existing expired-session, tenant, export/restore callback, PostgreSQL, both Compose and four topology/motion browser lifecycle/file cases remain required.

## Primary guidance

- [Spring interception and primary security-layer guidance](https://docs.spring.io/spring/reference/web/webmvc/mvc-config/interceptors.html)
- [Accepted bearer/session transport](../../ADR-0007-session-token-storage-and-browser-transport.md)
