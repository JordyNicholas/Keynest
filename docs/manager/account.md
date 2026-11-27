# Account and workspace

## Entry and navigation

On the homepage, choose **Start your vault** or **Sign in**. Registration requires a Keynest password of at least 15 Unicode code points. Your Keynest password authenticates you to the instance. The server vault key is configured separately. The shared sign-in and signup dialog clears inputs on cancellation and returns keyboard focus to its trigger.

Successful sign-in opens **Overview** by default. Explicit safe in-app return links can send you to a Vault item or the Account AI setup. A signed-out workspace URL redirects to the homepage. **Account settings** contains profile, security, password rules, optional AI, diagnostics, and help.

| Destination | Purpose |
| --- | --- |
| `/`, `/#generator`, `/#check` | Public homepage and local password tools |
| `/?auth=signin`, `/?auth=signup` | Sign-in and signup dialogs |
| `/overview` | Signed-in Vault summary |
| `/vault` | Find and manage saved items |
| `/vault/new`, `/vault/edit/:itemId`, `/vault/rotate/:itemId` | Add, edit, or rotate a saved login |
| `/tools` | Password tools using the selected account policy |
| `/account` | Account settings |

Desktop navigation uses the sidebar. On phones, scroll the top navigation horizontally to reach other sections. Use the [Vault guide](vault.md) or [password tools guide](password-tools.md) for the individual workflows.

## Change your password or sign out

- **Sign out** in the sidebar or Account settings ends the browser's current session. On a network error, Keynest clears the session in the current tab. Server revocation requires the API to receive it.
- **Sign out all devices** in Account settings verifies your current password and revokes all account sessions, including this tab.
- **Change password** in Account settings verifies the current password, saves the replacement, and issues a fresh session for the current browser. If the outcome is uncertain, sign in again with the new password first. See [runtime guidance](../operators/runtime.md#changing-the-account-password).

Owner-wide sign-out and password change are available on unreleased `main`. Vault item passwords are managed independently in [Vault](vault.md).

## What the overview means

| Element | What it shows |
| --- | --- |
| Vault items | Total saved items. Opens Vault |
| Need attention | Items already expired or with strictly less than 14 days remaining |
| Next expiry | Earliest future expiry. Opens that item when present |
| Expiry distribution | Five expiry groups with links to their Vault filters |
| Needs your attention list | Up to five items, earliest expiry first |
| Password rules | Effective account policy and links to change or check it |
| Recent item changes | Up to five current items ordered by last update |

**Refresh** loads a new snapshot. The timestamp describes when the counts and summaries were calculated. Recent changes show current items and their latest update timestamps. See [the overview API](../api/vault-browsing.md#overview) for exact count and expiry rules. For platform test evidence, see the [MVP rehearsal](../archive/mvp-release-evidence.md) and [supplementary checks](https://github.com/JordyNicholas/Keynest/issues/268).

## States and verification

An empty Vault offers a first-item action. An unavailable overview offers a retry or sign-in path. See the [browser rehearsal](../archive/mvp-release-evidence.md#full-stack-browser-rehearsal) and [supplementary platform checklist](https://github.com/JordyNicholas/Keynest/issues/268) for verification scope.
