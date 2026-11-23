# Bitwarden importer interoperability

Status: verification harness merged through maintainer-approved [#247](https://github.com/JordyNicholas/Keynest/pull/247) for #246 / #154. Third-party migration is deferred by the maintainer on November 23; destination downloads remain unavailable. This is importer/model verification, not a completed cloud-account import/save walkthrough or a supported production destination. No plaintext download route, destination activation, account credential or Vault secret retrieval is added.

## Pinned target and execution

The target is Bitwarden Clients tag `cli-v2026.10.1`, commit `91214c3b978edb5496a32a946451960fe7d44531`. The reviewed [upstream importer](https://github.com/bitwarden/clients/blob/91214c3b978edb5496a32a946451960fe7d44531/libs/importer/src/importers/bitwarden/bitwarden-json-importer.ts), its BaseImporter cleanup and actual Cipher/Login/URI export-to-view models run unchanged. The public [custom-file documentation](https://bitwarden.com/help/condition-bitwarden-import/) describes JSON/CSV import schemas; source/schema inspection alone is not compatibility proof.

The separate `Bitwarden importer interoperability` workflow checks out that exact upstream commit independently, with no persisted checkout credential. Keynest's Java serializer generates synthetic fixtures into the ignored API `target/bitwarden-interop` directory. A temporary spec is placed in the upstream importer test directory and run with its existing Jest configuration. No transformations/models are mocked and no upstream source is vendored into Keynest. The upstream checkout and packages retain their own licensing; they are not included in Keynest runtime artifacts.

Use JDK 21 and Bitwarden's pinned toolchain requirements (Node 24 / npm 11), then run:

```sh
mvn -B -pl apps/manager-api -am -Dtest=BitwardenInteropFixtureTest -Dsurefire.failIfNoSpecifiedTests=false test
git clone https://github.com/bitwarden/clients.git /tmp/keynest-bitwarden-check
git -C /tmp/keynest-bitwarden-check checkout 91214c3b978edb5496a32a946451960fe7d44531
python3 scripts/interop/run-bitwarden.py /tmp/keynest-bitwarden-check
```

The runner requires a clean exact checkout and bounded, marked generated fixtures. It installs only development verification dependencies in that checkout, with lifecycle scripts disabled. It uses no cloud account, server, destination persistence or user file argument. The temporary spec and diagnostic files are removed afterward; CI does not upload candidate files or reports. Output contains stage, fixed diagnostic codes and aggregate counts rather than values or exception excerpts. The generated fixtures contain only literal synthetic records; this is not a tool for real exports.

## Checked behavior and limits

Three batches contain 11 edge-case logins, 500 repeated-value logins with distinct source identities, and an empty export. Four upstream checks must execute without skips. The independent expected values cover exact names, usernames, passwords and nonblank URIs, leading/trailing spaces, quotes, CRLF, Unicode/emoji, formula-like prefixes, null/empty metadata, legacy short passwords and duplicate counts. Import results must contain no skipped-record errors or source/organization/key identities. A file with the encrypted flag is refused by the plaintext importer.

The existing candidate deliberately maps null, empty and whitespace-only websites to an empty URI list. This is an explicit observed candidate conversion, not exact preservation of a whitespace-only URL. The future compatibility review must disclose/handle such cases. Expiry/reminders, historical policy settings and original timestamps are omitted; they do not become destination account settings. These field losses need disclosure before a future plaintext download. No record may silently disappear, and a password must never be trimmed or spreadsheet-escaped.

Passing the harness establishes behavior only for this pinned parsing/model path and these fixtures. It does not verify web/desktop UI selection, server-side save limits, encryption/storage/sync, import duplicate handling, future versions, organization import, or browser CSV/1Password/Proton profiles. Keep all unverified migration destinations unavailable. A destination-specific import/save walkthrough with disposable data and a separately reviewed plaintext download/acknowledgment workflow remain gates.

## Evidence

[The interoperability workflow](https://github.com/JordyNicholas/Keynest/actions/runs/45193626493) passed on Keynest `a509bdf6058c24b6ffd0e01f4839e98d211c4249`, using upstream `91214c3b978edb5496a32a946451960fe7d44531`. Four checks executed without skips across the 11-login, 500-login and empty batches (511 synthetic logins total). This proves only the pinned parsing/model behavior described above; it does not establish actual destination save/sync or a supported migration download. Further migration work is deferred. Native encrypted export/restore remains separately available through #227/#231/#241/#245; see [the `.keynest` user guide](VAULT_RESTORE_WEB.md#using-a-keynest-file).

Rollback removes the fixture generator, isolated verifier/workflow and this record; no stored data, schema, key or application route is affected.
