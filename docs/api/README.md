# Manager HTTP API and formats

Paths below use the `/api/v1` base. Start the API using [local setup](../operators/local-setup.md) or [deployment](../operators/deployment.md). Account and Vault requests use `Authorization: Bearer <token>`. The server checks session validity and item ownership. See the [handler access inventory](../security/controls/api-authentication.md) for public, optional, and protected routes.

## Account and sessions

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | Retired on unreleased `main`: fixed HTTP 410 `REGISTRATION_REQUIRES_VERIFICATION`, no account or session |
| POST | `/auth/login` | Verify the account password and issue a session |
| POST | `/auth/me` | Check session/identity; unreleased `main` adds owner-only `emailVerified` |
| POST | `/auth/logout` | End the current session |
| POST | `/auth/logout-all` | Verify the current password and revoke all account sessions on unreleased `main` |
| POST | `/auth/password` | Change the account password and replace its sessions on unreleased `main` |
| GET, PUT | `/account/password-policy` | Read or update [account password rules](account-password-policy.md) |

See the [session transport decision](../architecture/decisions/ADR-0007-session-token-storage-and-browser-transport.md) and the separate contracts for [owner-wide sign-out](../architecture/decisions/ADR-0017-owner-wide-session-revocation.md) and [password change](../architecture/decisions/ADR-0018-account-password-change.md).

New signup is temporarily unavailable on this implementation branch while verified-email activation is built. Existing owners retain access. See the [activation transition guide](../operators/account-activation.md); this is a signup API compatibility change, not a new published release.

## Vault

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/vault/items` | List items or create one. Create accepts `generate: true` |
| GET, PUT, DELETE | `/vault/items/{id}` | Read metadata, update, or delete one item |
| POST | `/vault/items/{id}/reveal` | Open a sealed password with a `no-store` response |
| GET | `/vault/overview` | [Account summary](vault-browsing.md#overview) from owner-scoped metadata |
| POST | `/vault/items/search` | [Bounded metadata search](vault-browsing.md#search-page) |
| POST | `/vault/exports/preview`, `/vault/exports/download` | [Review and download an encrypted backup](vault-export.md) |
| POST | `/vault/restores/preview`, `/vault/restores/apply` | Review conflicts and apply an [authenticated restore](../architecture/decisions/ADR-0015-authenticated-native-restore.md) |

See [expiry inputs](../manager/vault.md#additive-api-contract), [native backup and schema](vault-portability.md), and [restore conflict resolution](../architecture/decisions/ADR-0016-restore-conflict-resolution.md).

## Password tools

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/tools/health` | Read the tools status and default policy ID |
| GET | `/tools/policy` | Read the effective policy, with optional matching `policyId` |
| POST | `/tools/generate` | Generate under the effective policy |
| POST | `/tools/validate` | Validate a sample for `create`, `update`, `rotate`, or `generate` |
| POST | `/tools/hash-password`, `/tools/verify-password` | Hash or verify an Argon2id value |
| POST | `/tools/seal`, `/tools/open` | Optional local demo operations |
| POST | `/ai/policy/explain` | [Optional authenticated built-in-policy explanation](policy-explanations.md) |

HTTP verification accepts Argon2id hashes within 64 MiB, three iterations, and one lane. Unsupported hashes return `valid: false`. Use the [Java library](../libraries/README.md) for hashes requiring different work factors. The standalone local API enables the documented seal/open demo. Docker disables it by default. See the [runtime guide](../operators/runtime.md) and [admission controls](../security/controls/availability.md) for hashing capacity, 429/503 behavior, and deployment limits.

## Duplicate creation

Vault creation returns HTTP 409 with `code: VAULT_ITEM_DUPLICATE` and `Cache-Control: no-store` when the signed-in owner already holds the same stored username, password and website URL. The label is not part of the match. Blank and omitted URLs both count as missing. Other owners are not candidates. This guard applies to creation; explicit restore choices remain governed by the restore contract.
