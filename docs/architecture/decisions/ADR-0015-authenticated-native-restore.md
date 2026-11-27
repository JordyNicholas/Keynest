# ADR 0015 — Authenticated native-file restore transport

## Status

Accepted through maintainer-approved [#241](https://github.com/JordyNicholas/Keynest/pull/241) for #240 / #154. The internal foundation is accepted through #235 / [ADR-0014](ADR-0014-native-restore-foundation.md).

The review/result metadata and duplicate/update decisions below were subsequently refined by accepted [ADR-0016](ADR-0016-restore-conflict-resolution.md), merged through #249. Its explicit Update/Ignore/Keep both contract supersedes the original unconditional append and no-deduplication behavior. The original transport decision is retained below as historical context; file, credential and single-use boundaries remain.

## Contract

Add authenticated POST `/api/v1/vault/restores/preview` and `/api/v1/vault/restores/apply`. Requests are strict JSON; file bytes use canonical padded standard Base64 in `file`. This avoids framework multipart disk spill. Both requests carry `backupPassphrase`. Preview authenticates the file and returns only `planId`, five-minute `expiresAt`, `itemCount` and `receivingPolicyUnchanged`. It returns no plaintext, item metadata or reusable elevated session. Apply sends the same file/passphrase, the current `accountPassword` and `planId`; it returns committed `restoredCount` and the unchanged-policy flag.

The account/receiver is derived exclusively from the existing bearer session. Fresh account password verification uses the existing shared password-work budget. Session identity and credential hash are checked again after verification and inside the receiving transaction before insertion and immediately after flush, before commit. The receiving account is locked/refreshed before the in-transaction checks. A failed check rolls the entire insertion back; no post-commit session check misreports a committed operation as a refusal. Revocation racing after the final check cannot retroactively undo a committed restore; no distributed revocation linearizability is claimed.

## Single-use plans

Keep at most 64 process-local plans. Generate random 256-bit opaque identifiers and store only identifier/session/credential-version digests, receiving UUID, exact-file/receiver consistency marker, count and expiry. Store no file, passphrase or plaintext. Purge expired plans on issue/consume; at most 64 expired entries may remain until the next call. No durable history is added.

Consumption atomically verifies owner/session/credential binding and removes the plan. Concurrent replays cannot both proceed. A wrong account password does not consume it; after valid reauthentication, changed file, busy work, decryption failure, rollback or success consumes it. A timeout/disconnect after commit can leave the client uncertain; reusing the plan is refused. Re-previewing is a new explicit operation and may create duplicates: no content deduplication or durable idempotent retry is implied.

Plans disappear on restart. Multiple replicas require preview/apply to reach the same process; otherwise the client must re-preview. This limitation fails closed rather than granting cross-node permission. A later durable/distributed design needs its own decision.

## Resource and privacy boundary

Bound request bytes to canonical Base64 container capacity plus 256 KiB for escaped credential/JSON overhead. Native 8 MiB/500-item/container bounds are unchanged. Reject duplicate/unknown fields, query parameters, trailing JSON, invalid encodings, malformed plans and oversized strings/bodies. One process-local nonqueued restore-request permit covers bounded parsing through credential/file cleanup. Unknown-length input is still bounded while reading. A slow body occupies that restore request permit; operators must configure ingress/body/read deadlines. Core export/restore and codec KDF gates retain their existing limits.

Always consume/clear owned credentials, request/file bytes and core payload buffers on success/refusal. Java/Jackson/HTTP/Base64/provider copies are best effort and cannot be reliably erased. No multipart/temp files, browser storage, request/body/header logging, AI context, or plaintext responses are introduced. Use TLS outside permitted local development. Proxies/APM must not capture these bodies.

A path filter sets no-store, Pragma, Expires and nosniff before size/rate/handler failures. Both routes join existing per-IP/per-route limits and fixed payload-free action/outcome events. Error codes are fixed and never echo parser/provider/SQL details or inputs. Existing per-JVM/rate/audit limitations remain; no durable attributed audit is claimed.

## Compatibility and validation

No database, Seal, key, stored payload or session format migration. The existing trusted internal restore overload remains for non-network tests/use cases; the HTTP handler always supplies its authorization callback. No public restore UI or agent tool is added here.

Tests cover exact HTTP restore, no writes during preview, fresh authentication, session/credential/file binding, stale/expired plans, concurrent one-use consumption, malformed/oversized/chunked bodies, admission cleanup, cache/rate/audit boundaries and revocation during receiving work. Retain the independent PostgreSQL database/key/restart test. Both packaged and hosted Compose smoke exercises perform encrypted export, receiving-owner preview/apply, replay refusal, fresh identity, exact password reveal and tenant isolation over real HTTP. Rollback removes transport/plans and fixed paths/actions; committed rows remain readable ordinary Vault items. Keep #154 and human file/UI walkthrough gates open.
