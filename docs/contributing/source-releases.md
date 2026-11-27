# Source-release preparation and publication

This workflow prepares the existing source distribution and five verification assets. It does not publish Maven/npm artifacts, a container image or a hosted service. Application version changes still use a reviewed PR. Existing release tags are never moved, deleted or overwritten.

## Operator sequence

1. Prepare the new stable numeric version (for example `0.1.2`) in a focused PR. Align root/module/standalone-consumer Maven versions, both npm package/lock roots, API launch/Docker JAR paths, a dated changelog heading and `docs/releases/<version>.md`. Include accurate changes, setup, upgrade, recovery and limitations. Do not reuse a published version for a new release.
2. Merge the reviewed preparation and wait for both **main-push** workflows on its exact commit: all seven application CI jobs and the source inventory/OSV workflow. Findings, unavailable matching or incomplete coverage block publication. A successful older SHA or PR merge checkout is insufficient.
3. In Actions → **Source release preparation** → Run workflow, select `main`, enter the version and the full expected main SHA, and leave **publish=false**. The expected SHA must equal this dispatch's main commit; a stale input or branch dispatch fails.
4. Inspect the read-only `source-release-bundle` artifact: four JSON metadata files, `SHA256SUMS.txt`, generated `release-notes.md` and `release.json`. Check the source, version, gate links, scopes and hashes. Workflow artifacts retain this metadata for seven days. Notes link to the reviewed version-specific source documentation.
5. The repository owner can dispatch again for the same reviewed main SHA/version with **publish=true**. This is the explicit release decision. The workflow revalidates same-source gate results and bundle contents before creating any tag/release. A non-owner run/re-run, PR, branch run or false publish choice cannot enter publication. If main has advanced, use a newly reviewed/current source and dry run rather than substituting an unverified SHA.
6. Verify the published tag target, final release URL and five asset digests. Keep prior release tags intact. Checksums and source inventories are unsigned metadata, not signed provenance or proof that no risks exist.

The workflow supports stable numeric versions only. Pre-release/version-policy expansion needs a reviewed change. Dispatch permissions are governed by GitHub repository access; public publication is additionally restricted to `github.repository_owner` as both original and triggering actor. This deliberately supports this personal repository, not delegated organization release roles. Delegation or an environment approval policy needs a separate reviewed design.

## Gates and permissions

The scripts reuse successful verification from the existing main-push workflows rather than duplicating or weakening their jobs. They query the exact source SHA, workflow path, event, main branch, latest run/attempt and each required job. Preparation needs `contents:read` and `actions:read`; rejection tests need only `contents:read`. No user PAT or new repository secret is required. Default-off publication alone receives `contents:write` plus read access to Actions metadata. Existing CI/GHCR publication behavior is untouched.

The selected inventory artifact must belong to the successful same-source scan run, be unexpired, have the expected name and match GitHub's archive digest. Download redirects never forward the GitHub token to the signed artifact destination. The bounded ZIP must contain exactly the four expected metadata files. Validate the manifest commit, all source input hashes, generator/scanner identities from that source revision's literal declarations (without executing it), both BOM output hashes/root versions/graphs/counts, every npm lock coordinate and all Java modules. The complete OSV package set must equal both BOMs and contain no advisory matches; empty/partial or changed reports fail.

Before publication, check version metadata and the clean checkout again; reject extra, linked, missing, oversized or changed bundle files. Re-query gate status and reconstruct notes/checksums. Tags are created atomically with a new ref; an existing/concurrently created tag or release stops the workflow. Create a draft, upload only `java.cdx.json`, `web.cdx.json`, `manifest.json`, `osv.json` and `SHA256SUMS.txt`, verify each server digest and the complete draft identity, then publish as latest. The internal bundle/notes receipt is not uploaded as an extra public asset. No automatic dependency update, advisory suppression, tag rewrite or destructive cleanup occurs.

## PR rehearsal and limits

PRs changing this workflow/scripts run rejection tests and a **read-only rehearsal against the PR's already verified base source**. The PR scripts fetch that base's real CI inventory and prepare a bundle; they do not pretend the base's results test the PR source or publish a new release. The usual application/inventory PR workflows separately verify the candidate. A rehearsal can fail if the base's required main CI is still pending or its seven-day inventory artifact has expired; rerun only after fresh legitimate evidence exists. It never falls back to another source or ignores a failed gate.

Tests cover wrong actor/event/ref/publish choice, stale/dirty/mismatched metadata, missing/failed jobs, incomplete/vulnerable scans, changed hashes, unsafe ZIP membership, existing/concurrent tags, upload digest failures, token separation and lost publication acknowledgments. Real production publication is not exercised merely to test this workflow. Keep that limitation explicit when reviewing a newly introduced publisher.

Inventories remain limited to the [documented source dependency scope](../security/dependency-scanning.md); no OS/base-image scan, signing, independent audit, live-provider verification or new manual platform evidence is implied. GitHub runner/tool availability, repository review controls and the trusted main workflow/scripts remain assumptions. A digest verifies bytes, not the honesty of compromised code or infrastructure. A failed or unavailable GitHub/advisory service fails the operation; there is no monitoring/publication SLA.

## Partial failure and recovery

State-changing API calls are not automatically retried. A tag can exist while draft creation/upload fails; a draft can remain when an asset fails validation. A lost final response can mean publication finished even though the job failed. The script does not delete or move those objects to make a rerun pass.

Inspect the exact tag, draft/release, asset states/digests and the job's source/receipt before deciding a recovery. Do not blindly dispatch again, force a tag or clobber assets. Correcting an existing draft/partial release is a separate explicit operator action with its own review. Preserve evidence; never put credentials, signed download URLs, Vault data or keys into logs/issues. Rollback of this automation removes its workflow/scripts and restores manual publication; it does not undo an already published release.
