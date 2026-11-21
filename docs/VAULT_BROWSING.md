# Bounded Vault metadata queries

These authenticated read endpoints support the approved home/overview/Vault design in [#199](https://github.com/JordyNicholas/Keynest/issues/199). They do not reveal passwords, fetch ciphertext, write history or track generation. Identity comes from the existing bearer session; there is no client-selectable owner. The legacy list/get/reveal/mutation endpoints remain compatible.

## Overview

`GET /api/v1/vault/overview` returns:

- `asOf`: one server timestamp used throughout the response.
- `counts`: `total`, `expired`, `soon`, `upcoming`, `later`, `unset`. These mutually exclusive buckets sum to `total`. Attention is `expired + soon`.
- `nextExpiry`: the earliest strictly future item summary, or `null`.
- `attention`: at most five expired/less-than-14-day summaries, earliest expiry first.
- `recent`: at most five current-item summaries, latest `updatedAt` first. This is not durable activity history; deleted items are absent and timestamps do not reconstruct past events.
- `policy`: the selected account policy ID and rules, using an inspection read that does not weaken the existing policy mutation lock.

Summaries contain only item ID, label, URL, expiry, creation and update timestamps. They omit username, password, ciphertext and owner identity. The public homepage's sample artwork never uses this endpoint.

## Search page

`POST /api/v1/vault/items/search` accepts a JSON body. Search text stays out of navigation/query strings. Empty `{}` selects page 0, size 10, all metadata fields, all expiry groups and label ordering.

| Field | Accepted value |
| --- | --- |
| `page` | Zero-based integer, 0–100000 |
| `size` | 10, 25, 30 or 50 |
| `query` | At most 200 UTF-16 code units; trimmed, case-insensitive literal substring |
| `field` | `all`, `label`, `url`, `username` |
| `expiry` | `all`, `attention`, `expired`, `soon`, `upcoming`, `later`, `unset` |
| `sort` | `label`, `expiry` |
| `itemId` | Optional UUID selecting a particular authorized item; unknown/other-owner IDs return an empty result |

Field/group/order names are case-insensitive. Search uses database lowercase semantics rather than accent removal or a promise of locale-specific full Unicode case folding. `%`, `_` and `!` are escaped as literals, not wildcards. Search and expiry/item selection combine with AND; `all` searches label OR URL OR username. Passwords are never searched. Criteria queries bind values rather than interpolate SQL. Invalid options return generic HTTP 400 without echoing or logging rejected metadata.

The response includes `asOf`, at most `size` metadata `items`, `totalMatches`, effective zero-based `page`, `size`, `totalPages`, and owner-wide expiry `counts`. Counts describe the whole owner's snapshot, not the current text query. An out-of-range last page is clamped to the current last page, including after deletion. Empty results use page 0 and totalPages 0. Metadata includes existing list-view fields and `createdAt`/`updatedAt`, with no secrets or owner identity.

Label ordering is lowercase label then item UUID. Expiry ordering is dated items first, earliest expiry, lowercase label, UUID; no-expiry items follow dated items. UUID breaks ties. Selecting an overview item may use `itemId` as a visible selected-item scope rather than downloading intervening pages.

## Exact expiry semantics

Let `remaining = expiresAt - asOf`, with one day exactly 86400 seconds:

| Bucket | Predicate |
| --- | --- |
| `expired` | expiry at or before `asOf` |
| `soon` | strictly future, less than 14 days |
| `upcoming` | at least 14 days, less than 30 days |
| `later` | at least 30 days |
| `unset` | no expiry |

`attention` combines expired and soon. Missing expiry is neutral; expiry status does not establish strength, breach exposure, policy compliance or external password usage. Rounded day labels do not determine membership.

## Consistency, privacy and operation

Each response uses a read-only repeatable-read transaction and one server `asOf`, so its aggregate counts and bounded lists share a database snapshot. Separate requests can observe intervening edits and time passing; refresh after mutations. Search is capped at 50 projected records. Overview performs a fixed number of aggregate/limited metadata queries rather than loading a Vault or issuing per-item lookups. It does not open encrypted password envelopes.

Success, anonymous denial, invalid input and malformed-body responses are `Cache-Control: no-store`. Validation and conversion failures are handled generically to avoid framework logs containing rejected query/item data. No new metadata diagnostics, analytics or AI egress is introduced. Operators still must keep HTTP body/access logging off or redacted; the existing plaintext metadata boundary remains.

The existing `(user_id, updated_at DESC)` index supports owner scoping/recent updates. This change adds no speculative index or schema migration. Aggregate counts and literal substring search can scan an owner's matching metadata; no large-dataset latency or high-availability guarantee is claimed. Use query plans/load evidence before choosing additional indexes.

Integration checks cover exact boundary fixtures, empty/no-future states, selected policy, page limits/clamping, literal search, stable sorting, owner/selected-item isolation, anonymous denial, cache headers and rejected-value logging. The packaged Compose smoke also exercises both endpoints, literal search and owner denial against PostgreSQL. Passing API checks do not validate the forthcoming overview/Vault UI or close the Windows/Edge release walkthrough. Rollback removes the new read endpoints without changing stored data, keys or existing API contracts.
