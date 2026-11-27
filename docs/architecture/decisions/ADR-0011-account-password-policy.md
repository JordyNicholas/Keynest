# ADR 0011 — Account password policy

## Status

Accepted November 20, 2026, following the maintainer's explicit approval of [PR #194](https://github.com/JordyNicholas/Keynest/pull/194) and [PR #195](https://github.com/JordyNicholas/Keynest/pull/195). Both are merged on `main`. The account-policy feature is implemented; follow-up human verification remains tracked in [release evidence](../../archive/mvp-release-evidence.md#november-20-policy-and-ux-fix-checkpoint).

## Decision

An account selects the built-in policy or custom password rules. Guest tools use the built-in policy. Authenticated generation, checking, Vault creation and password replacement resolve the account's current policy on the server.

Custom rules include length, character categories, ambiguous characters, whitespace, username matching, blocked strings and the built-in repeated-character pattern. User-supplied regular expressions are rejected. Account settings never change the master-password registration requirements.

Each saved custom policy receives a new identifier. New and replaced Vault secrets bind that identifier using the existing AAD format. Existing secrets retain their recorded identifier and remain readable after policy changes. Metadata-only edits preserve the envelope. A stale explicit policy identifier returns HTTP 409.

Policy edits and Vault writes lock the account row for a consistent selection. The nullable V6 account column defaults existing accounts to the built-in policy. Policy JSON contains configuration only. It is plaintext account metadata.

## Verification and rollback

Integration tests cover independent account policies, shared tools and Vault enforcement, stale selections and retained-secret reads. Migration tests cover V6 on H2 and PostgreSQL. Disable custom-policy writes before rollback, because the previous application cannot apply account rules to later password replacements. Keep the V6 data for recovery.
