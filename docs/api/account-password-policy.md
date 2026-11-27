# Account password policy

In Account settings, open **Password policy** and select **Keynest default** or **Custom account rules**. Save before opening another tool. Signed-in generation, checking, new Vault items and rotation use that selection. Guest generation uses the default policy. Guest temporary sample checks do not change an account.

Custom settings cover lengths from 8 to 128, required lowercase/uppercase/numbers/symbols, minimum category count, ambiguous characters, whitespace, saved username matching, blocked strings and four identical characters in a row. Character types are these four categories. Required types already count toward the minimum. Blocked strings match anywhere, ignoring case. Ambiguous-character exclusion applies to `0`, `O`, `I`, `l`, and `1` throughout the password, including transformed keywords.

Generation options choose a length, optional character types and keyword within the selected policy. Required types remain selected. Change account rules to change requirements. The generator obtains the selected rule document from the API, then creates the candidate locally using browser cryptographic randomness. It does not send keywords or generated candidates to the generation endpoint. Vault saves necessarily send the candidate to the Manager API for validation and encryption. A username restriction can still reject a standalone candidate when a saved username is supplied later. In Vault generation, the known username participates in the local check.

Keyword generation retries a bounded number of times and reports incompatible options instead of returning a candidate it knows violates the rules. Manual entries use the same server validation. Existing saved secrets remain readable after a rule change. New and replacement secrets use the current policy. Open forms carrying an older explicit policy identifier receive HTTP 409 and must reload. The master-password minimum for account registration remains separate.

## HTTP contract

`GET /api/v1/account/password-policy` returns `{ id, rules }` for the authenticated account with `Cache-Control: no-store`. `PUT` at the same path accepts `{ "mode": "default" }` or `{ "mode": "custom", "rules": ... }` and returns the selected policy. A bearer session is required. Custom rules use the fields returned by `GET`. At most 20 blocked strings of 1–64 characters and the single supported repeated-character regex are accepted. Arbitrary regexes are rejected. `maxAgeDays` is retained in the policy response for compatibility and does not override explicit Vault expiry controls.

`GET /api/v1/tools/policy` resolves the authenticated selection, or the default for a guest, with no-store. `POST /api/v1/tools/generate` and `/tools/validate` resolve that same selection. A provided `policyId` must match it. Vault create and replacement apply the account policy on the server even when the client omits an identifier. A guest cannot select another account's custom policy by passing its identifier.

The V6 migration adds nullable policy configuration to the account row. Existing accounts retain default rules. See [ADR-0011](../architecture/decisions/ADR-0011-account-password-policy.md) for data compatibility and rollback, and [release evidence](../archive/mvp-release-evidence.md) for recorded rehearsals and platform follow-up.
