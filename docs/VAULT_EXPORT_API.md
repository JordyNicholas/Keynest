# Encrypted Vault export API

Implementation proposal for #226; see [ADR-0013](ADR-0013-authenticated-vault-export.md). Requires the normal feature/security PR review. The portable file format is defined in [ADR-0012](ADR-0012-portable-backup-container.md) and [Vault portability](VAULT_PORTABILITY.md).

## Preview

`POST /api/v1/vault/exports/preview`, authenticated with the existing bearer session and JSON content type:

```json
{"destination":"keynest","scope":"all"}
```

Or `scope: "selected"` with an `itemIds` array of distinct canonical UUIDs. Selection contains at most 500 IDs and represents actual item identities, independent of UI pagination/search. All means the entire owned Vault, never just the current page. An all request may omit `itemIds` or provide an empty array; a selected request must contain at least one ID.

The response contains `destination`, `encrypted: true`, payload `version: 1`, `itemCount`, `snapshot` and `asOf`. No password, item metadata, stored envelope or source key is returned. The marker binds the selection and its read snapshot; it is not an authorization credential or elevation grant.

## Download

`POST /api/v1/vault/exports/download` takes the same selection plus:

| Field | Meaning |
|---|---|
| `snapshot` | The 64-character lowercase hexadecimal marker returned by preview. Changing selection, selected data/ciphertext or current account policy requires a fresh preview. |
| `accountPassword` | Immediate verification of the current user's Keynest account password. It does not become a reusable elevated session. |
| `backupPassphrase` | Passphrase protecting this file under ADR-0012. Retain it securely; the container has no escrow/reset mechanism. |

Do not put these fields into a URL, history, storage, logs, analytics or AI context. The body is strictly bounded JSON (96 KiB); duplicate/unknown fields, malformed UUIDs, unsupported destinations and trailing documents fail closed. Password values preserve their exact content. HTTPS is required outside the existing permitted local deployment context.

On success the response is encrypted binary with `Content-Type: application/octet-stream`, `Content-Disposition: attachment; filename="keynest-vault.keynest"`, `Cache-Control: no-store`, `Pragma: no-cache`, `Expires: 0` and `X-Content-Type-Options: nosniff`. The filename contains no account/item data. A future browser client can use that fixed filename without depending on cross-origin access to the disposition header. No plaintext CSV/JSON download is exposed by changing `destination`.

Every download verifies the current account password and session, checks ownership/membership/marker before decryption, captures a consistent database read, and checks the session again immediately before output. A selected missing/deleted/foreign item fails the entire request. For all exports, a Vault larger than 500 items fails explicitly; choose explicit subsets until a reviewed larger-export design exists. No partial success or silent omission is defined. Completion means the encrypted response was written, not that another instance restored it or a browser retained the file.

## Stable error codes

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `EXPORT_INVALID_REQUEST` | Invalid fields, duplicate data, unsupported destination or malformed body. |
| 401 | `EXPORT_SESSION_REQUIRED` | Missing, invalid, expired, changed or revoked session. |
| 403 | `EXPORT_REAUTH_FAILED` | Current account credential verification failed. |
| 404 | `EXPORT_SELECTION_UNAVAILABLE` | One or more selected IDs are unavailable to this owner. |
| 409 | `EXPORT_CHANGED` | Re-preview the selection because its selected data or account policy changed. |
| 413 | `EXPORT_REQUEST_TOO_LARGE` / `EXPORT_LIMIT_EXCEEDED` | Request or selection bound exceeded. |
| 422 | `EXPORT_EMPTY` | There are no owned items to export. |
| 503 | `EXPORT_BUSY` / `EXPORT_DATA_UNAVAILABLE` | Retry later, or investigate records/runtime configuration through safe operator tooling. No partial file was returned. |

The existing generic rate limiter returns its existing 429 shape and Retry-After header. Cache protection also covers size, limiter, malformed-input and unsupported-method responses. Errors do not echo credentials or identify which foreign item exists.

## Remaining feature work

A Vault UI with all/selected selection, compatibility review, passphrase entry and download lifecycle is still required. Application/database restoration needs separate preview/apply semantics and receiving-owner/item resealing; a file-codec round trip is not that evidence. Candidate browser/Bitwarden serializers remain unavailable until their actual destination import walkthroughs pass. Keep #154 and the human release walkthrough open.
