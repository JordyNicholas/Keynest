# Account overview and workspace entry

The reviewed design is tracked in [#199](https://github.com/JordyNicholas/Keynest/issues/199), with overview/workspace implementation in [#215](https://github.com/JordyNicholas/Keynest/issues/215). The standalone design preview remains synthetic; the application overview uses only the authenticated [metadata API](VAULT_BROWSING.md).

## Entry and navigation

Anonymous `/` introduces Keynest. Start your vault and Sign in open the shared Material account-entry dialog; mode-only URLs support refresh and bookmarks. `/account` retains the same form as a direct fallback and remains the settings/policy/AI destination.

Authenticated `/` and successful sign-in/signup without a requested destination open `/overview`. A new account sees its real empty-Vault first step, with Add item and Review rules actions. Guarded local Vault/overview destinations resume after sign-in. The existing fixed Account AI-setup handoff also resumes after guest sign-in; it makes no provider call until the user explicitly supplies a key and requests an explanation. Unrelated/malformed/external return URLs fall back to the overview. No credential or response payload is stored in navigation state.

Signed-in desktop navigation is a viewport-sticky rail. On phones it becomes the established horizontal scrollable menu, with the active destination highlighted and scrolled into view. Forms keep a readable maximum width; overview and Vault browsing use the wider workspace. A skip link and route-entry focus target the main content. Documentation and security links remain available in Account settings, including on mobile.

Sidebar and Account sign-out use one captured-session logout helper. Duplicate clicks on each pending control are blocked. A late response cannot clear a newer session. Network failure or a five-second bound clears the captured local session; server revocation still requires the API to receive the logout. This preserves the existing bearer/tab-session transport rather than claiming cancellation or a local timeout undoes server work.

## What the overview means

| Element | Source and destination |
| --- | --- |
| Vault items | Owner's current metadata count; opens `/vault` |
| Need attention | Expired plus strictly less than 14 days left; opens `/vault?expiry=attention` |
| Next expiry | Earliest strictly future item; opens its selected-item Vault scope |
| Expiry distribution | Five mutually exclusive groups from the same response; each labelled count links to its Vault filter |
| Needs your attention list | At most five current-item summaries, earliest expiry first; each opens that item |
| Password rules | Current selected account policy, with links to its settings and sample checking |
| Recent item changes | At most five current items ordered by `updatedAt`; equal creation/update timestamps are labelled Created, otherwise Updated |

The backend supplies one `asOf` and a repeatable-read snapshot for counts/lists/policy. Cards, distribution and attention labels use that timestamp. The visible snapshot caption and Refresh action make freshness explicit. A later request can legitimately observe a changed item or expiry group. Next expiry is “No upcoming expiry” when absent; it is never represented as zero days. An item opened from an older snapshot can be unavailable or no longer match a selected filter.

Metric numbers, units and supporting badges share a trailing alignment edge, including on phones. Distribution groups retain visible text/counts and keyboard-accessible links; color and hover are not the sole carriers of information. Expiry is not password strength, a breach score, current-policy compliance or proof of external usage. No-expiry items form their own neutral group.

Recent changes are not a complete history: they do not retain deleted items or reconstruct older events. There are no lifetime generation totals, provenance counters, historical events, AI analysis or background usage telemetry. Those contracts, privacy defaults and retention choices remain unresolved and require separate agreement.

## States and verification

Loading announces a pending view without sample metrics or invented zeroes. Empty accounts get a first-item path. Unavailable responses get a persistent explanation and retry/sign-in path rather than an empty-Vault message. Refresh clears the earlier snapshot while loading. Navigation, destruction, session change and logout cancel or invalidate pending work and discard account data. A 401 clears the stale local session. No secret endpoint is used to calculate the overview.

Automated component/route checks cover real-data metric rendering, destination filters/items, empty/no-future/no-attention states, policy summaries, persistent failure/recovery, request cancellation, session changes, default and guarded sign-in, guest AI handoff and sign-out. Core views make no provider call and work with AI disabled. These checks do not replace the actual Windows/Edge visual/full-stack walkthrough.

For that walkthrough, record separate Web/API revisions if needed and use synthetic accounts. Verify:

1. Anonymous product entry and smooth section navigation; dialog modes, refresh, Escape, close, focus return and the direct-account fallback.
2. Default sign-in/new-account entry to the overview and guarded Vault deep-link return.
3. Totals and groups against current Vault items, earliest future expiry, missing expiry, card/legend/item destinations and snapshot refresh after mutations. Use different expiry settings; API fixtures cover the exact 14/30-day and expired boundaries.
4. Bounded Vault paging/search/accordions using the [browsing checklist](VAULT_BROWSING.md#manager-web-browsing), including a default-size second page when enough items exist.
5. Recent current-item updates and selected policy after changing rules. Confirm these are not presented as full history or automatic revalidation.
6. Desktop rail, mobile sliding active navigation, 320px/enlarged-text reflow, common metric alignment, action layout, keyboard/focus and reduced motion. Check unavailable/retry and pending/logout cleanup with the API temporarily unreachable.
7. Existing create, reveal/conceal/countdown, edit, rotation, confirmed delete, custom rules, Account AI settings and AI-disabled operation. Keep credentials and revealed passwords out of evidence.

Retain the existing release and recovery gates in [MVP evidence](MVP_RELEASE_EVIDENCE.md). No schema, vault key, envelope, provider credential or session-transport migration is introduced by this frontend slice.
