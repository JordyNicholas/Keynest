# Source dependency inventories and vulnerability checks

This Phase 7 follow-up is separate from the immutable `v0.1.0` source release, which did not include an SBOM. A passing inventory-generation step does not establish that dependencies are vulnerability-free, or constitute an independent security audit.

## Workflow and scope

`Dependency inventories and vulnerability check` runs on pull requests, main pushes and manual invocation. It has only `contents: read`, no stored checkout credential, no secrets or publication permission, and a 20-minute job limit. Existing application verification remains in the separate CI workflow.

The Java inventory uses pinned `org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom`, CycloneDX 1.6, and all reactor compile/provided/runtime/system/test scopes. Maven builds/installs the reactor into the disposable runner before generation; normal tests still execute in CI. The Web inventory uses pinned npm 11.19.1 on Node 22, with `sbom --package-lock-only --sbom-format=cyclonedx` and dev/optional/peer inclusion. CI installs only this inventory tool globally with lifecycle scripts disabled; it does not install or change application dependencies. npm 10.9.9 emitted duplicate component identities in the initial rehearsal and was rejected. The validator requires the pinned generator version rather than relaxing coverage/graph checks.

Validation requires the expected source root versions, all four Java modules, all npm lockfile coordinates, package URLs, and a dependency graph without missing/duplicate references. A manifest records the actual checkout commit, input/output SHA-256 hashes, counts, tool versions and scope. PR runs normally inventory GitHub's synthetic merge checkout; use the manifest's commit rather than attributing it to an untested branch head. BOM timestamps/serial identifiers may vary; reproducibility here means a repeatable resolved inventory and explicit provenance, not byte-identical output across tool/runtime versions.

These inventories exclude OS/container base packages, generator/scanner toolchain dependencies and the standalone consumer example. They are source dependency metadata, not proof of deployed or reachable code, complete license compliance, signed provenance, or an image scan. Maven dependency resolution and the committed npm lockfile determine the package graph; registry/database coverage and changes remain relevant limits.

## Scan behavior and artifacts

OSV-Scanner 2.7.1 is downloaded from Google's official GitHub release and checked against the pinned Linux amd64 asset SHA-256 `fb7dab19a8bd9bbe5c3189f09d4b7ee1820c9553a23bac202648a56efd2f6ee8`. Only the two validated SBOM files are passed as scan inputs, with transitive re-resolution disabled. The scanner does not run its remediation commands or modify dependencies.

OSV's documented exit 0 means packages were scanned without matching known findings; 1 means findings; 127 is a general error and 128 means no packages found. Other error codes also fail the step. Before the real scan, a bounded rehearsal uses the validated Web inventory with a process-local unreachable proxy; it must return a scanner-error code rather than 0 or a findings code. Its temporary output and diagnostics are not uploaded. This verifies the selected tool's actual failure behavior without changing the real scan's environment. No `continue-on-error`, result suppression or automatic fix is configured. An API outage, parsing failure, absent report or empty scan must be investigated as incomplete verification, not converted to a clean bill of health. Inspect the workflow step outcome as well as the JSON report: an uploaded artifact alone does not prove a completed scan.

The scanner sends public package names/versions/ecosystems (and supported file hashes) to OSV's advisory service; its documented supplementary sources include deps.dev and package registries. No Vault, account, backup, key, token or runtime database is an input. Run this workflow on the public source checkout; future private dependencies require reviewing metadata egress first.

The seven-day artifact includes only `java.cdx.json`, `web.cdx.json`, `manifest.json` and `osv.json` when produced. Missing files and earlier failures remain visible in the run; do not interpret partially uploaded outputs as success. The executable, logs, environment, caches, database and build archives are not uploaded.

## Review and triage

Record the source SHA, workflow URL, successful inventory/scan steps, package/advisory identities and any matching-service failure. A finding is a triage input: confirm affected version/range, dependency path, build/runtime scope and applicability before choosing a patch. Keep development/test dependencies visible. Any proposed suppression needs a separate reviewed reason and expiry; this initial workflow defines none. Fixes use focused PRs and relevant regression/build checks, not unreviewed mass upgrades. A future release must generate inventories on its own exact revision; these artifacts are not retroactively attached to `v0.1.0`.

Local generation requires Java 21+/Maven 3.9+, Node 22/npm, and the same commands shown in `.github/workflows/dependency-security.yml`. From a clean committed checkout, run the Maven goal, create the output directory, generate/copy both inventories and run `python3 scripts/dependency_inventory.py`. The validator refuses tracked source changes to keep its commit attribution honest. Temporary untracked files are not scan inputs; only the explicit SBOMs are scanned. Use the pinned scanner on Linux amd64 or obtain and independently verify the official asset for your own platform.

Rollback removes this workflow, validator/tests and guide. It changes no application data, runtime dependencies or published tag.

## Primary references

- [CycloneDX Maven plugin and aggregate goal](https://cyclonedx.github.io/cyclonedx-maven-plugin/)
- [npm 10 SBOM command](https://docs.npmjs.com/cli/v10/commands/npm-sbom/)
- [OSV 2.7.1 release](https://github.com/google/osv-scanner/releases/tag/v2.7.1)
- [Pinned source-scan contract](https://github.com/google/osv-scanner/blob/v2.7.1/docs/scan-source.md)
- [Pinned output/exit-code contract](https://github.com/google/osv-scanner/blob/v2.7.1/docs/output.md)
