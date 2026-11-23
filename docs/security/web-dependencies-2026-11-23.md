# Web dependency remediation — November 23, 2026

Follow-up to #256/#257's first complete source inventory. The published v0.1.0 remains immutable. This candidate requires reviewed-change approval; it does not publish a maintenance release or claim an independent audit.

## Candidate and verification

Keep the approved Angular 21 stack and move framework/compiler peer packages to 21.2.26. Refresh the actual npm lockfile using the declared compatible ranges, without force or lifecycle scripts. CLI/build-angular resolve to 21.2.31; framework/compiler resolve to 21.2.26. Existing Material/CDK ranges remain. Package version 0.1.0 is unchanged on this development branch; new version/publication is a later reviewed step.

The lock refresh changed 281 existing package paths and added 58; these are dependency graph changes requiring full build/browser checks, not application code changes. Direct package majors remain unchanged. No arbitrary major-version override or advisory suppression is introduced.

The initial baseline scan reported 29 affected Web package versions, including runtime @angular/router and development/build dependencies. A local candidate scan using digest-verified OSV-Scanner 2.7.1 and the complete lockfile-generated CycloneDX inventory returned exit 1 with three affected package versions/advisory IDs remaining: glob 13.0.7, minimatch 10.2.6 and uuid 8.3.2. No runtime Web package match was returned in that candidate scan. That is database matching evidence, not proof of complete safety; bind the actual candidate lock hashes/tree before treating it as review evidence, and rerun on the final source revision.

| Remaining development dependency | Advisory | Condition / next step |
| --- | --- | --- |
| glob 13.0.7 | GHSA-3g3m-2fjr-6pw4 | AbortSignal cancellation can leave a filesystem walk running. Reported fix is 14.0.2, outside current parent ranges; inspect callers and upstream compatible updates before any override. |
| minimatch 10.2.6 | GHSA-g3hv-75wg-m249 | Pattern amplification with optimizationLevel >= 2. Reported fix is 11.0.1, outside current ranges; check use of untrusted patterns and upstream compatibility. |
| uuid 8.3.2 through sockjs | GHSA-w5hq-g745-h8pq | v3/v5/v6 external-output-buffer bounds, not every UUID API. Inspect the actual SockJS call site; patched major lines need compatibility review. |

All three are development dependencies in the candidate lock. Do not hide them or equate development scope with harmlessness: source/build inputs and CI isolation still matter. The complete scanner remains red while findings remain; this change defines no ignore/baseline exception.

## Applicability and compatibility

Angular's GHSA-57xq-rjx2-v5xh explicitly excludes client-only SPAs: it concerns SSR query-parameter retention. Keynest's Web build is client-rendered, with API ownership enforcement separately in Java. Package matching is therefore not proof of that particular issue's reachability. Nonetheless, use the supported patched 21.x framework and retain route/owner/session/cleanup/policy regression coverage. Other Router advisories and toolchain paths require their own conditions; do not extrapolate the SSR exception to every finding.

Require reproducible npm ci, Angular browser tests, production/API URL/date build checks, and packaged API/Web compatibility before merge. No auth transport, data/schema, encryption or Vault behavior redesign is included. Rollback restores prior package/lock metadata and known advisory matches, with no database rollback.

## Primary references

- [Angular 21.2.26 release](https://github.com/angular/angular/releases/tag/v21.2.26)
- [Vendor SSR advisory](https://github.com/angular/angular/security/advisories/GHSA-57xq-rjx2-v5xh)
- [glob advisory](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4)
- [minimatch advisory](https://github.com/advisories/GHSA-g3hv-75wg-m249)
- [uuid advisory](https://github.com/advisories/GHSA-w5hq-g745-h8pq)
