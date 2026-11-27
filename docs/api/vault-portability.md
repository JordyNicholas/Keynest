# Vault portability

Keynest supports native encrypted export and restore. Use the [Manager user guide](../manager/backup-and-restore.md) to make or restore a file. The file recovers items. [Database backup and recovery](../operators/database-migrations.md) covers accounts and full-instance state.

## Current implementation boundary

The [v1 payload schema](../schemas/vault-export-v1.schema.json) describes the JSON inside a `.keynest` file. The [backup container decision](../architecture/decisions/ADR-0012-portable-backup-container.md) defines authenticated encryption, passphrase derivation, limits, and compatibility. The [export API](vault-export.md) handles selection, review, password confirmation, and download. The [restore transport](../architecture/decisions/ADR-0015-authenticated-native-restore.md) binds an expiring review to the account, session, and file, and [conflict resolution](../architecture/decisions/ADR-0016-restore-conflict-resolution.md) defines explicit add, update, and ignore choices.

The receiving API authenticates the file and verifies the receiving owner before applying changes. It reseals passwords under that instance's key and item binding. Separate database/key/restart verification is recorded in the [restore foundation decision](../architecture/decisions/ADR-0014-native-restore-foundation.md). Browser results and limits are recorded in the [release evidence](../archive/mvp-release-evidence.md).

## Product decision

The supported destination is a Keynest encrypted backup. Third-party formats are research for [#154](https://github.com/JordyNicholas/Keynest/issues/154). Credential fields in the JSON payload are plaintext inside the authenticated container. The browser and Bitwarden prototype serializers also produce plaintext and are unavailable as download destinations.

## Native payload v1

`format` is `keynest.vault`; `version` is the integer `1`. `exportedAt` is a UTC instant. All timestamps use ISO-8601 UTC strings ending in `Z`. JSON object key ordering is not significant. Strings retain their content without trimming, case changes, normalization or spreadsheet escaping.

| Field | Meaning |
|---|---|
| `accountPolicy` | Snapshot of the account's effective policy identifier and rules at export time, including the built-in rules when selected. Uses the bounds and restricted pattern supported by ADR-0011. |
| `items[].id` | Source item UUID, for reconciliation within this file. Never an authorization credential. Adds assign fresh receiving item/owner identities. Explicit updates preserve the selected receiving identity; both seal under the receiving instance's existing AAD contract. |
| `label`, `url`, `username`, `password` | Login data; optional URL/username represented explicitly as `null`. Password is plaintext **inside** this intermediate payload. |
| `policyId` | Identifier recorded on that item. Historical identifiers are preserved; historical policy rule snapshots are not available in the current database and must not be fabricated. |
| `expiresAt`, `lastRotatedAt`, `notifyBeforeDays` | Existing expiry/rotation metadata, with explicit nulls where absent. |
| `createdAt`, `updatedAt` | Original metadata timestamps, not new export timestamps. |

No login email, account UUID field, password hash, master credential, session, AI credential, server key, DEK/KEK, outbox data or stored Seal envelope is part of this payload. Existing policy identifiers can themselves contain account UUIDs; this metadata must be treated as sensitive rather than advertised as anonymous. An item ID alone cannot deduplicate safely across arbitrary source instances. File v1 does not itself define deduplication; accepted ADR-0016 defines receiving-owner candidate matching and explicit Add/Ignore/Update choices without changing the file format.

All documented fields are required, including nullable ones. Unknown fields, unknown versions, missing values, duplicate object keys, duplicate item IDs, unsupported policy regexes, invalid timestamps, trailing JSON documents and inappropriate types are rejected. Parser/schema exceptions are replaced with a fixed message without a cause or input excerpts.

Initial bounded implementation: at most 500 items and 8 MiB per payload/output, nesting depth 12, string length 8192 UTF-16 units (in addition to JSON Schema code-point bounds), and field-specific schema limits. Account-policy minimum length may not exceed maximum length. An empty vault is representable, while the export API reports an empty Vault before download. Selection limits must be disclosed before reauthentication; an over-limit all-items request must fail explicitly rather than become a partial export. Limits and nullable-field behavior are part of the v1 contract and must stay synchronized with tests.

The schema is shipped as an application resource and published in docs; a source test checks that they are identical. No runtime schema URL fetch is required.

## Destination research and compatibility

Official documentation reviewed November 22, 2026. These are documented import capabilities, not Keynest compatibility certifications.

| Destination | Documented behavior | Proposed Keynest profile |
|---|---|---|
| [Google Password Manager](https://support.google.com/accounts/answer/10500247?hl=en) | CSV file import with `url`, `username`, `password` columns. | Browser-login CSV; a `name` column additionally carries the label where recognized. Verify treatment of that column and optional values. |
| [Microsoft Edge on Windows](https://support.microsoft.com/en-gb/edge/import-your-favorites-and-passwords-in-microsoft-edge) | Password CSV import through browser settings. | Browser-login CSV candidate; verify on Windows/Edge before exposing the profile. |
| [Firefox](https://support.mozilla.org/en-US/kb/import-login-data-file) | CSV login/password import. | Candidate browser profile; confirm exact headers, duplicate behavior and field acceptance independently. |
| [Bitwarden](https://bitwarden.com/help/condition-bitwarden-import/) | Its own JSON and CSV schemas; JSON supports more item types. | Individual-vault JSON. First implementation maps logins only, not organizations/collections or other item types. |
| [1Password](https://support.1password.com/import-1password-com/) | Generic CSV import with item-type and column mapping. | Separate instructions and mapping profile, still to implement and validate. |
| [Proton Pass](https://proton.me/pass/download) | Documents generic CSV and named-provider import. Its [Edge guide](https://proton.me/support/pass-import-edge) uses the Chrome provider. | Confirm the specific importer/schema before choosing an adapter; sharing a serializer does not imply every import path is equivalent. |

The Bitwarden candidate writes `encrypted: false`, an empty `folders` list and login items with `type: 1`, `name`, `favorite: false`, `reprompt: 0`, and `login` containing `uris`, `username`, `password`, `totp: null`. Missing URLs produce an empty URI list; no website is invented. Keynest identifiers are not copied to destination item IDs. Expiry/reminder/policy settings and timestamps are omitted, and the compatibility screen must disclose this before download.

The browser candidate writes UTF-8 CSV with `name,url,username,password` and CRLF record separators. Every value is quoted; embedded quotes are doubled. Embedded commas, newlines, Unicode, empty usernames and leading `=`, `+`, `-`, `@` must retain their exact values. Do not prefix apostrophes or change a credential to mitigate spreadsheet formulas: this is an import file, not a spreadsheet report. Warn against opening migration exports in spreadsheet applications. The candidate rejects the entire operation when any selected item lacks a website; the future review screen must identify affected items and offer explicit edits/exclusions or a suitable alternative destination. Do not silently omit items.

See the [pinned Bitwarden importer experiment](../archive/bitwarden-interoperability.md) for the scope and result of that research.
