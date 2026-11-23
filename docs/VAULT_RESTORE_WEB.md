# Native encrypted-file restore workflow

Implemented through maintainer-approved [#245](https://github.com/JordyNicholas/Keynest/pull/245) for #244 / #154, building on the authenticated API accepted and merged through #241 / [ADR-0015](ADR-0015-authenticated-native-restore.md). Implementation and automated checks do not close the human Windows/Edge or release gates.

## Using a `.keynest` file

1. Run a Keynest API and web build containing #245. Sign in to the account that should receive the items, on this instance or another compatible Keynest instance.
2. Open **Vault → Restore** and choose the exported `.keynest` file.
3. Enter the **backup passphrase chosen during export** and select **Review encrypted file**. Review does not write items.
4. Check the item count and append/duplicate disclosure. Enter the **receiving account’s current password**, then select **Verify and restore items**.
5. On confirmed success, check the refreshed Vault. Current filters still apply. Verify a restored item before relying on the backup. If the outcome is uncertain, close and refresh before attempting another restore.

The file is an authenticated encrypted container holding Keynest’s versioned native JSON. It does not need the source instance’s encryption key or original account password. The backup passphrase cannot be reset. Restore keeps existing items and the receiving account policy; a repeated backup can add duplicates. Store the passphrase separately from the file. Other password managers require their own migration formats; `.keynest` is the native Keynest restore path. Retained-key database backups remain necessary for complete instance/account recovery.

## Workflow and privacy contract

Enter Restore from the signed-in Vault. Opening it conceals revealed passwords; export and restore dialogs cannot overlap. Select an encrypted `.keynest` file and enter its backup passphrase. The browser checks bounded v1 framing before uploading, reads one file at a time, and sends canonical Base64 only to the configured authenticated API. Ordinary/plaintext files and oversized files are refused locally; cryptographic authentication, schema validation, owner binding and resource limits remain server decisions. No filename is sent.

Review writes nothing. It shows only item count and the short-lived plan's local expiry time. The receiving account policy and existing items remain unchanged. Restores append fresh identities and preserve original passwords and supported metadata/dates, including expired dates. Repeating a backup after a new review can add duplicates; no deduplication or durable idempotency is claimed.

Apply requires the current account password. Credentials are cleared from inputs immediately on apply. The same selected File is reread and independently checked by the server against the reviewed exact bytes. The one-use plan has the existing account/session/credential/file binding. There is no automatic apply retry. A validated committed response closes the dialog, refreshes the same account's Vault and announces the count; current filters still apply. Canceled/failed/late cross-session results cannot refresh another account.

The backup passphrase stays in its masked input between preview and apply for at most the review lifetime. File handles, credentials, owned read buffers, pending readers/HTTP subscriptions and plan references are cleared on expiry, close, tab hiding, page hide, session change and destruction. Selecting another file clears credentials and the previous review; editing a passphrase invalidates review, cancels pending work and clears the account credential while retaining the selected file. Review responses retain only count, expiry, unchanged-policy flag and opaque plan identifier. No file/payload/credentials are added to browser storage, URL/history, logs, telemetry or AI context. JavaScript/browser/HTTP strings and copies cannot be reliably erased; the user's original selected file remains on their device. Existing session-token storage is unchanged.

Unknown apply outcomes (including lost connections, unrecognized responses, or a generic transaction failure that may conceal a lost commit acknowledgement) clear the payload and block further submissions in that dialog. A persistent message requires closing and refreshing the Vault before another restore. Tab hiding after apply was sent follows the same rule. Closing/unsubscribing cannot retract bytes or undo a server commit. Explicit fixed refusal codes invalidate the review; retry requires a new user-initiated review. Consumed/expired/restarted or differently routed plans fail closed under ADR-0015's process-local limits. Expiry cancels idle previews; a sent apply is not interrupted just because the plan's review deadline passes.

Native backups do not replace separately retained database/key backups, provide account recovery or imply third-party importer compatibility. This UI adds no crypto, schema, key, session transport, agent capability or runtime dependency. Rollback removes the dialog/entry point and restores prior export copy; already restored rows remain ordinary Vault items.

## Verification

Component/HTTP tests cover framing and size refusal before upload, canonical encoding across chunk boundaries, exact preview/apply requests, no writes during review, immediate credential/read-buffer cleanup, no extra storage, no double submission, edits/expiry, abort/close/page-hide handling, fixed refusals, uncertain outcomes, malformed responses, session changes and unauthorized responses. Vault tests cover concealment, dialog exclusion and confirmed-success refresh for the same account. Retain the real PostgreSQL cross-database/key/restart tests and packaged/hosted API export-to-restore smoke from #241.

Before release, use disposable records to download a real file in Windows/Edge, select it in a different account/instance, preview/apply and reveal the preserved password. Verify keyboard/focus return, narrow layouts, reduced motion, wrong phrases, session expiry, cancel and lost-response behavior, duplicate disclosure and current-filter messaging. Record exact SHAs, browser versions and actual results. Request mocks alone do not prove browser file IO or the complete human journey.
