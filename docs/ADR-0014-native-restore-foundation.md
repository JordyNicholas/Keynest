# ADR 0014 — Native encrypted-file restore foundation

## Status

Proposed for #234 under #154. This is a separate security-sensitive change. The maintainer approved the existing payload/container and export API/UI through #217, #219, #227 and #231; those approvals do not approve this restore implementation.

## Decision and boundary

Add an internal application service for restoring a v1 encrypted file into an existing receiving owner. It is not registered as a controller, CLI, scheduled job, AI/MCP tool or browser capability. There is no new way for a remote caller to write Vault data.

The service accepts an explicitly selected receiving owner UUID, encrypted bytes, file passphrase, and (for mutation) a marker returned by preview. This UUID is a trusted internal caller argument, not an authorization credential. Before any HTTP/CLI/agent exposure, a separate reviewed transport must derive it from an authenticated session, freshly verify the receiving account password, recheck revocation and credential changes, enforce body/rate limits, and bind an expiring single-use plan to the session, owner and exact file. Do not expose these methods directly as an endpoint.

Preview authenticates/decrypts the complete existing container and validates the native schema before returning only item count, a consistency marker, time and confirmation that the receiving policy is kept. It writes nothing and does not return passwords, item metadata or source policy contents. Empty files are rejected.

The marker is SHA-256 over a fixed domain, fixed-size receiving UUID and exact encrypted file bytes. It binds the receiver and file, not authorization, session identity, policy approval, expiry or single use. It is not a signature. Apply checks it before KDF/decryption, then independently decrypts/authenticates/schema-validates the same owned file copy. A caller can construct markers; trusted-caller authorization remains a prerequisite. Repeated internal calls intentionally create additional rows; transport replay/idempotency handling is still a gate.

## Receiving transaction and identity

Restore is all-or-nothing in its own transaction. Lock/recheck the existing receiving account, assign a fresh random UUID for every item, and use persist rather than merge so existing identities are never overwritten, even on collision. Source owner identities, sessions, account credentials, notification outbox state and server keys are never imported. Existing records are retained and no deduplication is implied.

Preserve login strings, historical item policy identifiers and supported timestamps/expiry/reminder lead times, including nulls and already-expired dates. Do not apply today's receiving password rules to historical passwords, trim values, synthesize historical rules, replace the receiving account policy, or adopt source item IDs. The source account-policy snapshot is informational and not installed. Notification delivery continues to require the receiving account's existing consent/verification contract; source consent/outbox is not restored.

Reseal each password with the existing receiving instance key and unchanged item-v1 AAD using receiving owner/new item ID/historical item policy ID. This changes neither the Seal wire format nor key hierarchy. Reuse VaultService's existing sealing operation; its owned UTF-8 plaintext buffer is now cleared in finally. No separate cipher/provider or runtime dependency is added.

## Resources, failure and compatibility

The existing 500-item/8 MiB native payload and authenticated container limits remain unchanged. Clone only a bounded encrypted input to prevent an in-process caller changing the bytes between marker and decode. Keep the caller's encrypted file unchanged. Share the existing nonqueued bulk-work gate with export; keep it through transaction commit/rollback and owned file/payload cleanup. Codec KDF admission remains its existing separate bound. No temporary files, payload stores or durable restore plans are created.

Always consume/clear the passphrase, including owner, marker and admission failures. Clear owned file copies and remove JSON references before releasing the work gate. Java strings/JSON/provider/GC copies cannot be reliably erased; existing dump/telemetry hardening remains necessary. Failures are fixed, cause-free codes; never return/log parser, provider, SQL or input excerpts. No new audit/HTTP events are claimed in this internal slice.

No database or stored-envelope migration is needed. Rollback removes the service and its new callers; existing restored rows remain ordinary receiving-owner Vault items readable through the existing key/AAD implementation.

## Evidence and remaining gates

Integration tests must cover exact metadata/password preservation under a stricter receiving policy, receiver isolation, wrong passphrase/tamper/changed file or receiver, empty files, busy work, credential/file/payload cleanup and rollback after a flushed partial insert.

A Testcontainers PostgreSQL test exports from one application/database/key, closes that application, restores to an independently migrated database under another key and owner, retains existing receiving items, checks ownership/readability and metadata, then restarts the receiving application and reveals again. Record actual CI execution rather than counting a skipped Docker test as proof.

This foundation does not implement a public restore journey. Authenticated preview/apply HTTP, one-use plan/replay semantics, rate/body/cache/audit boundaries, restoration UI, Windows/Edge handling and third-party importer walkthroughs remain separate gates. Keep #154 open.
