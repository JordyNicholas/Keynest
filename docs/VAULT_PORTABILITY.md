# Vault portability

Status: **[proposed]** implementation contract for [#154](https://github.com/JordyNicholas/Keynest/issues/154), following the maintainer's November 22 agreement on destination-first exports and JSON as the native data contract. Encrypted native export is implemented through approved PRs #227/#231. The internal receiving-database restore foundation is merged through #235; authenticated transport is merged through #241; restoration UI is merged through #245; verified third-party migration remains unfinished; this is not a complete public backup/restore claim.

## Current implementation boundary

The first slice, merged through maintainer-approved [#217](https://github.com/JordyNicholas/Keynest/pull/217), adds a pure, bounded serializer in `manager-api` and a published [v1 payload schema](schemas/vault-export-v1.schema.json). It has no Spring registration, HTTP endpoint, database access, filesystem output, secret retrieval, cryptography, import mutation or UI. There is no new way to obtain a Vault password. The schema and serializer remain reviewable together before a download surface is introduced.

The serializer can encode/decode a native JSON payload and translate it into individual-vault Bitwarden JSON or a browser-login CSV candidate. Unit tests establish data fidelity and rejection behavior; they do **not** establish acceptance by a third-party importer. Destination profiles must remain unavailable in production until their import walkthroughs pass with synthetic data and recorded application versions.

The encrypted-container slice is tracked in [#218](https://github.com/JordyNicholas/Keynest/issues/218), approved PR [#219](https://github.com/JordyNicholas/Keynest/pull/219), and [accepted ADR-0012](ADR-0012-portable-backup-container.md). It adds a passphrase-protected file codec using Argon2id and the existing Seal implementation. It is not an HTTP export, database restore, or UI; the maintainer has approved its concrete container/custody contract. The full portability feature and actual separate-instance database restoration remain open under #154.

The authenticated encrypted export API (#226) and Vault UI (#230) are merged through maintainer-approved PRs [#227](https://github.com/JordyNicholas/Keynest/pull/227) and [#231](https://github.com/JordyNicholas/Keynest/pull/231). See [the API contract](VAULT_EXPORT_API.md), [accepted ADR-0013](ADR-0013-authenticated-vault-export.md), and [the web workflow](VAULT_EXPORT_WEB.md).

The internal native-file restoration foundation [#234](https://github.com/JordyNicholas/Keynest/issues/234) is merged through maintainer-approved [#235](https://github.com/JordyNicholas/Keynest/pull/235), under [accepted ADR-0014](ADR-0014-native-restore-foundation.md). Its PostgreSQL test verifies receiving-owner resealing across separate application contexts/databases/keys and a receiving restart. It adds no public restore route. Authenticated transport and expiring single-use apply plans are merged through maintainer-approved [#241](https://github.com/JordyNicholas/Keynest/pull/241) / [accepted ADR-0015](ADR-0015-authenticated-native-restore.md), with packaged/hosted PostgreSQL/API round trips. Browser restoration is merged through maintainer-approved [#245](https://github.com/JordyNicholas/Keynest/pull/245); see [how to use a .keynest file](VAULT_RESTORE_WEB.md#using-a-keynest-file). Pinned upstream importer checks are being added in #246 / [the interoperability record](BITWARDEN_INTEROP.md); destination support remains gated.

## Product decision

The user selects a purpose and destination. Keynest selects the format and explains compatibility. No single file format is promised to work everywhere.

| Purpose | Contract |
|---|---|
| Keynest backup or instance migration | Versioned native JSON payload inside a separately reviewed authenticated-encryption container. No plaintext native backup download by default. |
| Migration to another manager | Destination-specific JSON or CSV, based on the receiving importer and verified preservation of supported fields. |
| Advanced generic export | Explicitly chosen plaintext format, with no universal-compatibility claim. Defer until its use and disclosures are reviewed. |

JSON is a serialization format, not encryption. Bitwarden JSON and browser CSV outputs in this slice are plaintext. They must not be described as protected backups. A ZIP alone also supplies no confidentiality.

## Native payload v1

`format` is `keynest.vault`; `version` is the integer `1`. `exportedAt` is a UTC instant. All timestamps use ISO-8601 UTC strings ending in `Z`. JSON object key ordering is not significant. Strings retain their content without trimming, case changes, normalization or spreadsheet escaping.

| Field | Meaning |
|---|---|
| `accountPolicy` | Snapshot of the account's effective policy identifier and rules at export time, including the built-in rules when selected. Uses the bounds and restricted pattern supported by ADR-0011. |
| `items[].id` | Source item UUID, for reconciliation within this file. Never an authorization credential. Restore must assign new item/owner identities and seal under the receiving instance's existing AAD contract. |
| `label`, `url`, `username`, `password` | Login data; optional URL/username represented explicitly as `null`. Password is plaintext **inside** this intermediate payload. |
| `policyId` | Identifier recorded on that item. Historical identifiers are preserved; historical policy rule snapshots are not available in the current database and must not be fabricated. |
| `expiresAt`, `lastRotatedAt`, `notifyBeforeDays` | Existing expiry/rotation metadata, with explicit nulls where absent. |
| `createdAt`, `updatedAt` | Original metadata timestamps, not new export timestamps. |

No login email, account UUID field, password hash, master credential, session, AI credential, server key, DEK/KEK, outbox data or stored Seal envelope is part of this payload. Existing policy identifiers can themselves contain account UUIDs; this metadata must be treated as sensitive rather than advertised as anonymous. An item ID alone cannot deduplicate safely across arbitrary source instances; no automatic deduplication is defined by v1.

All documented fields are required, including nullable ones. Unknown fields, unknown versions, missing values, duplicate object keys, duplicate item IDs, unsupported policy regexes, invalid timestamps, trailing JSON documents and inappropriate types are rejected. Parser/schema exceptions are replaced with a fixed message without a cause or input excerpts.

Initial bounded implementation: at most 500 items and 8 MiB per payload/output, nesting depth 12, string length 8192 UTF-16 units (in addition to JSON Schema code-point bounds), and field-specific schema limits. Account-policy minimum length may not exceed maximum length. An empty vault is representable, although the future UI can disable an empty download. Selection limits must be disclosed before reauthentication; an over-limit all-items request must fail explicitly rather than become a partial export. Limits and nullable-field behavior are part of the v1 contract and must stay synchronized with tests.

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

## Destination-first workflow (next slice)

1. Enter Export from Vault. Choose backup versus migration and destination.
2. Choose all items or selected items. Selection persists across pages and filters with an explicit count and clear-selection action. “All” always means the whole owned Vault, not merely the current page.
3. Review item count, format, encryption state, field losses, item limits and blockers. Metadata preview never reveals passwords. Changes in selection/destination invalidate the review.
4. Reauthenticate immediately for the chosen export. Authorization remains server-side and owner-scoped. A mixed owned/foreign selection fails entirely without identifying which foreign item exists. A stale or expired session cannot obtain a download.
5. Download through an explicit action. Destination selection does not transmit credentials to that service. A plaintext export requires a specific readable-password acknowledgment.
6. Show destination-specific import and deletion guidance. Completion means a file was prepared/download initiated, not that the destination import succeeded.

Reuse the approved Material visual language, keyboard focus, responsive layouts, transient errors and reduced-motion behavior. Keep this workflow separate from AI configuration and tools. Selection state must contain only IDs, never secrets, and clear on logout/account switch. Do not place export credentials in URLs, browser storage, analytics or history.

## Security and restoration gates before a download endpoint

The encrypted container, password-based key derivation, parameters, AAD/header authentication, unlock UX and offline restore contract require a dedicated reviewed design and negative tests. Reuse approved cryptographic primitives and the existing Seal contract where applicable; do not alter stored envelopes, keys, golden vectors or session transport. The first serializer PR did not implement export-password custody. The accepted container contract and its wire/KDF/cleanup tests live in ADR-0012; a user-reachable export journey still requires application integration and review.

Fresh password verification must be bound to the actual authenticated user and export request; failure paths need rate limiting, no-store and sanitized fixed-action HTTP events. ADR-0009 events are best-effort and unattributed, not a durable audit trail. Any stronger audit/retention requirement needs its own decision. No bulk export capability belongs in AI/MCP tools.

Fetch a bounded, owner-scoped snapshot; authorize every selected ID before any decryption. Fail on missing, changed or inaccessible records rather than silently reducing the count. Bound concurrent secret work. Clear owned byte/char buffers on success and failure; JVM strings/JSON trees cannot be reliably erased, so keep references short-lived and retain existing diagnostic hardening. Do not log parser causes, payloads, selection metadata or plaintext. Proxies/APM must not capture these requests/responses.

Restore must be an explicit preview/apply workflow: authenticate the file before parsing its plaintext, enforce byte/item/KDF limits before expensive work, reject malformed/unknown formats, and reseal entries under the destination owner/item AAD. Preserve original login values and supported metadata; never overwrite existing items, adopt a source owner, replace the account policy or reinterpret historical policy rules implicitly. A backup must not be advertised as restorable until a separate-instance round trip is actually verified. Server encryption-key independence is a required acceptance test, not an implemented claim.

## Focused PR sequence and evidence

1. **Format foundation:** schema, pure native codec, Bitwarden JSON/browser CSV candidates, bounded and sanitized failures, data-preservation tests. No export endpoint or UI.
2. **Encrypted portability and restoration:** reviewed container/credential custody and restore semantics, cross-instance round trip, wrong-password/tamper/resource-bound negatives.
3. **Authenticated export application flow:** scoped selection/snapshot, fresh credential verification, rate limits, no-store, sanitized events, cross-owner and cancellation failures.
4. **Vault workflow:** destination selection, cross-page selection, compatibility review, disclosures, download lifecycle, import guidance and accessibility tests.
5. **Interoperability walkthrough:** actual imports into each advertised application with synthetic passwords, including quotes/commas/Unicode/newlines, blank metadata, duplicates and missing URLs. Record versions, dates, counts and observed losses; keep unverified destinations hidden.

No feature/security PR merges without the maintainer's chat approval of that reviewed change. The export work is an explicitly requested next feature; it does not close the ongoing MVP walkthrough or release gates.
