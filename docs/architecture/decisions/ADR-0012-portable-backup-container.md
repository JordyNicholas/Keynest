# ADR 0012 — Passphrase-protected portable backup container

## Status

Accepted following the maintainer's explicit approval of [PR #219](https://github.com/JordyNicholas/Keynest/pull/219) in chat on November 22, 2026. The change builds on the approved native JSON foundation in [#217](https://github.com/JordyNicholas/Keynest/pull/217). The codec is a file-format layer; parent [#154](https://github.com/JordyNicholas/Keynest/issues/154) remains open for application export, restoration and UI.

## Context and scope

A portable backup must open independently of the source installation's server key and user/session database. A plaintext native JSON file, ZIP or copied database envelope does not meet that requirement. The accepted Seal v1 already provides AES-256-GCM; the native payload serializer already enforces the export schema and resource limits. This layer composes those contracts, without altering stored Vault data or inventing a cipher.

The implementation is a pure file codec in `manager-api`: no bean registration, route, filesystem output, bulk Vault retrieval, database mutation, AI service or cloud dependency. It is **not** a working backup/restore journey yet.

## Decision

1. The user chooses a **backup passphrase** for this file. The container never incorporates a server key, account master-password hash, session token or AI key. The future UX should recommend a separate passphrase and explain that losing it makes this file unrecoverable; Keynest has no escrow/reset mechanism for this container.
2. Passphrases contain at least 15 Unicode code points, at most 1024 UTF-16 code units, and are not entirely whitespace. Valid surrogate pairs are required. Encode as UTF-8 without trimming or Unicode normalization. These bounds are fixed for both v1 creation and opening, rather than dependent on the current account's password policy. A passphrase is not stored or returned; the codec consumes and clears the caller's `char[]` even after invalid-input/busy failures. Caller-owned JSON trees and input files are not modified.
3. KDF profile **1** is Argon2id version 0x13 (v1.3), 65536 KiB memory, 3 passes, 4 lanes, 16-byte random salt and 32-byte output. This matches RFC 9106's second recommended profile. Use the existing pinned `argon2-jvm` 2.11 raw-key API, not the encoded authentication hash or its textual bytes. This does not change authentication hashing or its configured work budget.
4. Generate a new salt with `SecureRandom` per file; let the existing `KeynestSeal` generate a fresh 12-byte nonce. Seal the complete UTF-8 native v1 JSON payload with AES-256-GCM and a 128-bit authentication tag. Do not compress it. Metadata, account policy and item count remain within ciphertext; total file/payload length remains observable.
5. Use a fixed binary container with a 30-byte header followed by exactly one existing Seal v1 envelope. Bind the entire header as the GCM AAD, including the salt, profile, version and length. Require the fixed Seal version/algorithm/key identifier as well. No user-selectable algorithms or file-supplied cost parameters are accepted.
6. Reject unsupported profiles/versions, excessive sizes, malformed framing and unexpected Seal headers **before** invoking the KDF. Authenticate the complete encrypted payload before parsing JSON. Wrong passphrases, tampering, provider exceptions and malformed decrypted data return a generic cause-free failure. A fixed busy outcome is distinct, allowing a future transport to reject excess work without waiting indefinitely.
7. One non-blocking backup-work permit is shared across codec instances in a JVM. The permit covers serialization/encryption or decryption/validation, is returned on failure, and is independent of authentication's budget. This limits concurrent backup KDF allocation to one 64 MiB operation per process; it does not bound the entire process, JSON/AEAD copies, caller work or another JVM. Future endpoints still need authorization, password reauthentication, rate limiting, request-size limits and a bound on snapshot/decryption work before invoking the codec.

## Wire contract v1

Filename extension: `.keynest`; content type: `application/octet-stream`. A native JSON payload is still the data contract; encryption changes its on-disk representation.

| Offset | Length | Encoding / expected value |
|---|---:|---|
| 0 | 8 | ASCII magic `KEYNESTB` |
| 8 | 1 | Container version `0x01` |
| 9 | 1 | KDF profile `0x01` as specified above |
| 10 | 16 | Random Argon2id salt |
| 26 | 4 | Unsigned big-endian byte length of the following Seal envelope |
| 30 | Remaining bytes | Seal v1 envelope: version 1, algorithm 1, key ID `keynest.backup.v1`, nonce, ciphertext, GCM tag |

The first **30 exact bytes** are the GCM AAD. No JSON canonicalization, string concatenation of arbitrary headers, trailing bytes or optional fields are involved. The Seal envelope's key ID is a constant domain label, not a reference to an operator key. The key ID/version/algorithm bytes must match the fixed profile before opening. Salt/header tampering changes the authenticated context; nonce/ciphertext/tag tampering fails GCM authentication. No metadata preview is trustworthy before successful authentication.

For this key ID, Seal overhead is 49 bytes; total container overhead is 79 bytes. The payload limit remains 8 MiB / 500 items. Maximum file size is 8,388,687 bytes. The declared envelope length must exactly equal the remaining file length and must contain a nonempty payload. Truncated/appended data cannot be accepted by ignoring bytes. A newer reader must retain old supported profiles explicitly; changing profile 1 in place would break existing backups. No fallback to a weaker KDF is allowed.

## Custody, threat model and restoration boundary

A stolen file permits offline passphrase guessing. The KDF raises cost; a predictable passphrase remains weak. This is not recovery of a lost account master password and is not zero knowledge: the existing Manager can access Vault plaintext, and a future server-side export would temporarily process the backup passphrase and plaintext. The input/output transport must be HTTPS outside permitted local use; reverse proxies/APM must not capture bodies. No file/passphrase/payload may enter logs, AI context, traces, URLs or browser storage.

Owned plaintext bytes, derived key bytes, transient salt/header/envelope arrays and caller passphrase arrays are cleared in `finally`. The raw KDF API clears its UTF-8 password conversion. Seal follows its existing best-effort buffer hygiene. JSON trees, Java strings, provider/JNA/JCA copies, native allocations and GC/heap copies are not reliably erased by this contract. The caller must keep references short-lived and retain runtime dump hardening.

Opening this file yields a schema-validated payload, **not** restored database rows or authorization. The future restore application must authenticate the receiving owner, preview content without revealing secrets, require an explicit apply action, allocate new item IDs, reseal under the destination owner/item AAD and key, and preserve supported original metadata. It must not adopt source ownership, overwrite entries, replace an account policy or fabricate historical rules implicitly. None of these mutations are implemented by this codec. A retained-key database backup and this portable file are different recovery mechanisms.

## Evidence required

- Independently generated Python/OpenSSL fixture: Argon2id plus AESGCM, manually assembling the documented framing and unchanged Seal layout; Java opens it and the deterministic Java writer matches it. Only synthetic credentials/entropy are used. The generator is a developer verification tool, not a production dependency.
- Round trips through distinct codec instances without a source-server key, exact Unicode/metadata fidelity, different salt/nonces on repeated creation.
- Wrong passphrase, changed salt/nonce/ciphertext/tag, changed magic/profile/version/key ID, size/length mismatch, truncation, appended bytes, valid-tag malformed/unsupported JSON, invalid passphrases, busy work and provider failure.
- Instrumented tests establish that malformed headers/oversized inputs cannot start a KDF and that key/passphrase buffers are cleared on covered success/failure paths; permit contention/failure does not leak a work slot.
- Relevant PR CI, unchanged existing Seal golden vectors, authentication, database migrations, consumer boundaries and deployment smoke checks. Full database restore, Windows/Edge file UX and third-party imports remain separately unverified.

## Compatibility and rollback

This is additive and not reachable through Manager routes. No existing account, database schema, envelope, AAD, key hierarchy, authentication flow or deployment variable changes. Revert the codec and this specification before it is exposed if necessary. Once backups are distributed, preserve a compatible reader; deleting or silently changing profile 1 would strand those files.

## References

- [RFC 9106 §4 and §7.4](https://www.rfc-editor.org/rfc/rfc9106.html#section-4), Argon2id parameter profiles and recommendations.
- [argon2-jvm](https://github.com/phxql/argon2-jvm), pinned 2.11 source/API inspected from its published source artifact.
- [ADR-0003](ADR-0003-keynest-seal.md), existing Seal v1 and best-effort buffer hygiene.
- [ADR-0006](ADR-0006-manager-vault-key-and-aad.md), existing Manager key and item AAD, unchanged here.
- [Vault portability](../../api/vault-portability.md), native payload and application/security gates.
