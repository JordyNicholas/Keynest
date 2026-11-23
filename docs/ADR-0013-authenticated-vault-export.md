# ADR 0013 — Authenticated encrypted Vault export

## Status

Accepted following explicit maintainer approval and merge of PR #227. Native payload v1 (#217) and its encrypted container (#219 / ADR-0012) remain unchanged. The approved export UI is merged through #231. Parent #154 remains open for restoration, interoperability and the human walkthrough.

## Decision

Expose two synchronous, authenticated POST endpoints for **Keynest encrypted export only**: preview and download. No unverified plaintext destination profile is selectable, and no export capability is added to AI/MCP tools. There is no database restore or frontend workflow in this slice.

Preview accepts an explicit all/selected scope, verifies the entire owner-scoped selection, enforces the 500-item bound, and returns a count plus a consistency marker. It never decrypts a password or returns item metadata/ciphertext. Database-side ciphertext lengths are checked before bounded entity retrieval. Missing/foreign selected IDs yield the same failure; empty or excessive selections never become partial files.

A marker is SHA-256 over length-framed domain/owner/scope/selection/current-policy and selected-row state, including ciphertext and AAD version. Selected IDs are sorted; input ordering does not affect it. Null and empty fields remain distinct. This is **not** a signature, approval token, bearer credential or reusable elevation. It grants no permission and has no claimed one-use/expiry property. Every download independently authenticates and verifies the current account password. A client that invents a marker cannot bypass ownership or reauthentication; it can only fail the consistency check. No preview/credential/plan store, signing key, sticky session or cloud service is needed.

Download takes the same selection, marker, account password and a separate backup passphrase. The account credential is verified immediately through the existing shared password-work budget, without issuing another session. Recheck the current session/identity/credential hash after that verification and again immediately before output. Reject a stale selection/policy/data marker before any password is opened. A read-only repeatable-read transaction captures a consistent selected-row/policy view; later concurrent writes do not retroactively change that captured point in time.

Only after complete selection/marker checks does the existing Vault reader open each password with its unchanged item AAD/key contract. Assemble native JSON preserving password values and supported metadata; do not revalidate/rewrite historical passwords against today's policy, adopt source ownership, or change database rows. The reviewed container encrypts that JSON with the supplied backup passphrase, salt and nonce. The source instance key is never exported. The export passphrase and account password are separate inputs; the API does not require them to differ, although the future UX should recommend that.

## Admission, output and failure handling

Requests are JSON-only, reject unknown/duplicate fields/trailing documents, and are bounded to 96 KiB even with unknown/chunked length. Account-password verification allows nonempty credentials up to the decoder's 32768 UTF-16-unit string bound, without applying the registration minimum to legacy credentials. Backup passphrases retain ADR-0012's 15-code-point / 1024-unit, valid-surrogate, nonblank contract and exact UTF-8 semantics. Neither credential is trimmed. Existing login/registration limits are unchanged.

The existing per-IP/per-route limiter includes both endpoints. A new process-local, non-queuing permit allows one bulk operation at a time, covering reauthentication, source reads/decryption, container creation, synchronous output and cleanup. Admission is acquired after bounded body parsing, so a slow request body does not occupy the bulk-work permit. Authentication hashing and the container retain their existing separate work budgets. Per-process gates/limits are not distributed coordination; operators still need appropriate request/response timeouts, proxy body limits and deployment capacity controls.

The output uses a fixed generic filename, octet-stream content type, nosniff and no-store/Pragma/Expires headers. Write and clear the encrypted response bytes before releasing bulk capacity, including on disconnect. A client cancellation cannot retract bytes already sent. There is no temporary file, durable export artifact, pre-signed URL, cached response or stored password. Credentials, input bytes and owned plaintext/key buffers follow existing best-effort clearing; JSON/JVM/provider copies cannot be reliably erased. Requests/responses and sensitive selection/marker data must not be logged or captured by APM/proxies.

A path-scoped filter sets no-store before size/limiter/handler failures. Canonical export routes add two fixed action names to ADR-0009's existing payload-free HTTP outcome stream. No actor, selected IDs/count, marker, credential, metadata, exception payload or file content enters those events. They remain best-effort observations, not durable attributed audit or proof that a recipient received/imported a file.

## Compatibility and recovery limits

This adds endpoints, not migrations, account/session transport changes or a new encryption/key hierarchy. Existing Vault CRUD/reveal/policy behavior remains unchanged. A portable file can be opened by the reviewed codec without the source server key, but application/database restoration is still a separate implementation and verification gate. Do not advertise a complete restore journey or close #154 until preview/apply restoration, receiving-owner resealing and a separate-instance database round trip are verified. Third-party imports and Windows/Edge file UX also remain pending.

Rollback removes the new application endpoints and restores the previous fixed-action/path lists. Files already issued still require their compatible container/payload reader; do not remove or mutate those accepted formats as part of endpoint rollback.

## Evidence

Tests must cover exact all/selected encrypted output, metadata preservation, owner isolation and all-or-nothing behavior, stale data/policy/ciphertext markers, password failure, session revocation before output, malformed/oversized/chunked input, rate/cache boundaries, busy work, provider/disconnect cleanup and logging redaction. The disposable packaged/hosted Compose smoke exercises real HTTP encrypted output against PostgreSQL. This evidence does not substitute for the forthcoming restore/UI/interoperability walkthroughs.
