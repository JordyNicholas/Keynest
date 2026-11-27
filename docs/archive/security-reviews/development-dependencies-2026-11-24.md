# Development dependency remediation — November 24, 2026

## Evidence and scope

After the approved backend and frontend patches (#260/#261), the complete [combined source scan](https://github.com/JordyNicholas/Keynest/actions/runs/45384768384) validated 151 Java and 958 Web package coordinates. Its checked-out PR merge commit is `2b514b2668ff218cdd3fe59043bef5b2059ccb8e`, tree `f7e1e58c64021ceff86717d8e5bace03106590a1`. The manifest records input/output SHA-256 hashes and tool versions. OSV-Scanner 2.7.1 returned exit 1 with three Web development package matches and no Java matches. The unavailable-matcher rehearsal returned error 127. Inventory success is separate from scanner success.

The three matches are glob 13.0.7 / GHSA-3g3m-2fjr-6pw4, minimatch 10.2.6 / GHSA-g3hv-75wg-m249 and uuid 8.3.2 / GHSA-w5hq-g745-h8pq. Development scope does not establish harmlessness. This change patches dependencies and introduces no advisory exception, suppression or changed scanner exit policy.

## Targeted overrides and compatibility

Current upstream parent ranges do not admit the fixed major lines. Use exact overrides under the five actual consumers rather than changing every occurrence of a package. Existing glob 7 and minimatch 3 consumers retain their declared dependencies. No direct Angular/Material/framework major changes, application behavior, Java dependencies, data/schema, key/session transport or Seal format changes are included.

| Parent | Dependency change | Inspected caller / compatibility check |
| --- | --- | --- |
| @npmcli/package-json 7.0.5 | glob 13.0.7 → 14.0.6 | `lib/normalize.js` uses the named CommonJS `glob` Promise API for README, native-build and executable discovery. Exercise actual package preparation with a disposable fixture. |
| cacache 20.0.4 | glob 13.0.7 → 14.0.6 | `lib/util/glob.js` uses the named Promise API; removal and verification use bounded cache paths. Exercise write/read, orphan reclamation, retained content and full cache removal. |
| @tufjs/models 4.1.0 | minimatch 10.2.6 → 11.0.9 | `dist/role.js` uses the named `minimatch` function on individual path components. Exercise allowed and denied delegation patterns, file extensions and path depth through the actual model. |
| ignore-walk 8.0.0 | minimatch 10.2.6 → 11.0.9 | `lib/index.js` uses `Minimatch`, `match`, `globParts` and negation with default optimization. Exercise ignored/retained files, hidden files, nested paths and negation. |
| sockjs 0.3.24 | uuid 8.3.2 → 11.1.1 | `lib/transport.js` imports `require('uuid').v4` and calls it without an external output buffer. UUID 11 retains CommonJS. Open an actual loopback SockJS XHR session and verify its v4 connection identifier. |

Before inspection, affected package tarballs and the parent sources were fetched from committed lockfile registry URLs and verified against their SHA-512 integrity values. No downloaded source was executed during that source inspection. Compatibility tests subsequently run the installed dependencies with synthetic fixtures and a short-lived loopback HTTP server; they do not contact an external provider or handle Vault data.

These are intentional changes outside upstream parent ranges, with compatibility risk. Functional coverage of the actual consumers, reproducible `npm ci`, Node 22 CI browser tests and both production build configurations are required. The new tests run with the existing `test:config` / `test:ci` command. They cover the interfaces Keynest's toolchain uses, not all upstream APIs or possible inputs. Local Node 24 results do not substitute for Node 22 CI. No force installation is used.

The overrides are temporary maintenance controls: remove each when its maintained parent admits a fixed version and the complete scan and consumer tests pass. Re-evaluate after Angular CLI/toolchain updates. Do not expand these into global major overrides.

## Verification and release handling

Initial local evidence: lock regeneration and `npm ci --ignore-scripts --no-audit --no-fund` succeeded; all 38 configuration/date/consumer tests passed, including five new consumer checks. Both production build configurations passed. Full candidate browser/build/API/Compose CI and complete source inventory matching must be attached before merge or release.

The maintainer approved #260/#261 and conditional maintenance publication after risk verification. These additional transitive-major overrides are a separate reviewed security change. Keep them unmerged until their evidence is reviewed and approved in chat. The published v0.1.0 tag remains unchanged. Do not describe a clean dependency match as proof that the application has no risks or as an independent audit.

Rollback restores the prior package/lock metadata and removes the added compatibility test command/file. It requires no database rollback but restores the three known advisory matches.

## Primary references

- [glob advisory and fixed line](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4)
- [minimatch advisory and fixed line](https://github.com/advisories/GHSA-g3hv-75wg-m249)
- [uuid advisory and affected APIs](https://github.com/advisories/GHSA-w5hq-g745-h8pq)
- [UUID 11.1.1 fix backport](https://github.com/uuidjs/uuid/releases/tag/v11.1.1)
- Candidate package sources and integrity digests: `apps/manager-web/package-lock.json`.
