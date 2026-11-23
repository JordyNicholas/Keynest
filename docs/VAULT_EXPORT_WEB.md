# Vault encrypted export workflow

Implemented through maintainer-approved PR #231 (#230 / #154), using the authenticated API merged in #227. Neither this workflow nor file-codec tests complete the application/database restore gate.

Enter Export from the signed-in Vault. Select individual rows with the labelled checkboxes outside each expansion header. The selection stores IDs only, persists across pages/search/expiry filters, and displays a total plus Clear selection. It is limited to 500 and clears on account/session change or leaving the Vault. Opening Export conceals revealed passwords.

The dialog offers the reviewed Keynest encrypted `.keynest` destination only. Choose all items (the entire owned Vault, independent of displayed pages/filters) or the captured explicit selection. Review requests metadata-only preview; no secret is revealed to the UI. Changing scope, cancellation, tab hiding, failed requests or completed download invalidate the preview. The server independently verifies selection ownership, count and snapshot.

After review, supply the current account password and a separate export passphrase, confirmed twice. The passphrase has the container's minimum 15 Unicode code points and maximum 1024 UTF-16 units. Account passwords are not trimmed and keep the existing legacy-compatible bound. Inputs are cleared immediately on submit, close, tab hiding, session change or destruction. Credentials are sent in the authenticated POST body over the configured transport, never URLs/history/storage/telemetry. JavaScript/HTTP copies cannot be securely erased; cancellation cannot retract a server operation or bytes already sent.

The HTTP response remains an encrypted Blob. A temporary object URL starts a generic `keynest-vault.keynest` browser download; it is revoked shortly afterward or when the dialog is destroyed. Success says download *started*, not file receipt/import verified. Errors are fixed UI messages based on status; Blob error bodies are not decoded or displayed. Session expiry clears the local session; selection changes cannot reuse a previous preview. No plaintext export destination or AI access is added.

The dialog points to Restore in the Vault; see [the proposed restoration workflow](VAULT_RESTORE_WEB.md). Retained-key database backups remain necessary. Do not claim offline recovery, close #154, or advertise third-party compatibility before separate-instance restoration and importer walkthroughs pass.

## Verification

Automated coverage checks exact authenticated all/selected request shapes, no secret retrieval during preview, credential input clearing, passphrase mismatch rejection, encrypted Blob/download lifecycle, stale previews, cancellation/session changes, and ID-only selection across pages/filters. API tests/Compose smoke in #227 independently cover encrypted output and owner/stale checks.

Before release, exercise Windows/Edge download/save/cancel with disposable records, keyboard/restore focus, narrow layouts and reduced motion; verify the actual downloaded file through the reviewed codec and separately verify application restore when implemented. Automated request mocks do not prove browser file retention or receiving-database restoration.

Rollback reverts this UI slice; there is no database migration or persisted selection.
