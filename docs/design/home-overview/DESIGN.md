# Keynest public home and account overview

**Status: proposed design for discussion, not an approved production interface.**

Issue [#199](https://github.com/JordyNicholas/Keynest/issues/199). Baseline inspected: `3e9ca6222649cb0ad9bf30c51fd2323c4c3a3528`, November 20, 2026. The maintainer wants a complete, tailored design and has explicitly removed rush from this feature. This does not declare a new release date or waive tests and approvals.

Open [the interactive prototype](prototype.html) in Edge. It is a standalone design document with synthetic data, no network requests, credentials, persistence or real sign-in. Screen, state and phone-width controls are review tools, not proposed product chrome. The existing product routes are unchanged. All styles and scripts are embedded, so the HTML can be opened directly without installing anything.

## 1. Confirmed direction and unresolved choices

Confirmed by the maintainer: a public product overview; an account-specific overview after sign-in; distinct pages for distinct workflows; a tailored responsive visual design; a forest palette; optional AI and portable self-hosting; useful visual summaries; and exploration of usage history and generation statistics. The requested shared error refinement is tracked separately in #197/#198.

The exact layouts, wording, dashboard destination, history scope and retention described below are **proposals**. Sample counts are illustrations, not observed usage. Do not convert these into accepted architecture or product claims merely because this document merges.

## 2. Evidence and visual direction

The November 20 research inspected page content and actual screenshots at desktop 1440×1000. Mobile 390×844 captures were inspected for the Proton campaign and 1Password. A screenshot does not establish hover behavior, animation quality, conversion or accessibility compliance.

| Reference | Observed pattern | Keynest interpretation |
| --- | --- | --- |
| [Proton](https://proton.me/pt-br) | Centered large promise, differentiated actions, later product and trust content | Introduce purpose before tools; maintain clear action hierarchy |
| [Proton Pass campaign](https://proton.me/pt-br/l/bf-pass-offers-2026-pfm-ppo) | Focused hero, emphasized offer, differentiated cards, onboarding sequence; mobile artwork occupies substantial space | Borrow sequencing and hierarchy; prefer real product preview over a promotional illustration |
| [1Password](https://1password.com/pt) | Large type, contrasting hero, product UI preview, desktop navigation simplifies on mobile | Preview the actual proposed product; adapt to Keynest's smaller capability set |
| [Norton](https://my.norton.com/extspa/passwordmanager) | Prominent product identity and install action; generator has a separate destination | Make account entry and generator independently discoverable |

These references inform composition. Their logos, artwork, claims, prices and testimonials are not Keynest assets. No independent-audit badge, fabricated testimonials, customer counts, zero-knowledge claims, breach monitoring, autofill or agent features should be implied.

### Existing tokens, verified in Keynest

Source: [`keynest-tokens.css`](../../../apps/manager-web/src/styles/keynest-tokens.css). Exact values below come from that file, not inferred competitor CSS.

| Role | Existing value |
| --- | --- |
| Canvas / ink | `#0c1410` / `#e8f0ea` |
| Primary mint / primary ink | `#c6e2cf` / `#0c1410` |
| Muted / subtle text | `#b7c8bc` / `#9bb5a4` |
| Attention / error | `#f0d18a` / `#f0b7a8` |
| Display / body / mono | Fraunces / IBM Plex Sans / IBM Plex Mono, with local fallbacks |

### Proposed refinements

Retain the wordmark and palette. Introduce clearer surface elevation, generous section spacing, a restrained serif display heading and compact sans-serif operational text. Proposed surfaces `#152019` and `#1c2b22`, healthy expiry `#8fbf9f`, neutral expiry `#8b9b99`, panel radii 15–20px, and a 4/8px spacing scale are choices for review, not existing extracted tokens.

Public content is capped near 1200px. Operational overview content can expand with a desktop navigation rail; forms retain readable widths. Primary operational labels and notes use proposed 12–14px type, body content 14–16px, and larger primary values; incidental snapshot/section markers use 10–11px. At phone widths summary cards become readable horizontal rows rather than squeezing the labels into tiny cards. The prototype is framework-free and uses installed font fallbacks; it is not a pixel-identical Angular Material implementation. Production should reuse Keynest tokens and Material buttons, dialogs, focus handling and notifications.

The imagery is original HTML/CSS UI composition with synthetic labels, not an image asset or actual account screenshot. No reference-site artwork or remote image/font dependency is included in the prototype.

## 3. Information architecture

| Context | Proposed behavior |
| --- | --- |
| Visitor opening `/` | Product homepage |
| Successful sign-in with no requested destination | Account overview, proposed `/overview` |
| Sign-in required for an existing deep link | Return to that authorized destination after sign-in |
| Password generation / policy check | Keep `/tools/generate` and `/tools/check` as separate workflows |
| Vault filtering | Dedicated `/vault` view, proposed explicit expiry query parameter |
| Policy and AI setup | Remain in Account settings, separate sections |
| Account settings and troubleshooting | Keep existing workflows; no diagnostics clutter on the public hero |

Proposed signed-in navigation: Overview, Vault, Password tools, Account. Desktop rail is shown for discussion. At phone width, retain the maintainer's horizontal, scrollable navigation preference with the current destination highlighted; do not silently introduce a different mobile navigation convention. About/security documentation remains reachable from the application. The overview does not embed generator, policy-check or reveal forms.

## 4. Public homepage specification

1. **Identity and entry:** wordmark, how it works, security, documentation, sign-in. At narrow widths, keep sign-in visible and avoid a dense header. Production must retain access to the secondary destinations through a keyboard-accessible compact menu or equivalent visible links.
2. **Hero:** candidate heading “Your passwords. Your rules. Your Keynest.” Supporting sentence names generation, storage, management and self-hosting. Primary “Start your vault” opens account entry; secondary “Try the generator” opens its dedicated route. Existing users can sign in directly.
3. **Preview:** synthetic account overview illustrates what users will find. Label examples as samples. A screenshot or decorative image must not hide the explanation or primary action on mobile.
4. **Product principles:** open-source license, self-hosting, account rules and AI-optional operation. These are product properties, not a claim of market uniqueness.
5. **How it works:** choose rules; generate or enter and save; review expiry and rotate through dedicated workflows. Do not imply automatic external password changes.
6. **Security boundaries:** encrypted password values, server-side decryption, current plaintext metadata, operator key/backup responsibilities. Link the threat model. Public copy remains understandable without dumping implementation configuration into onboarding.
7. **FAQ:** account requirements, generator policy fetch, guided rotation and audit status. Optional AI is specifically built-in-policy explanation, not a general AI password manager.
8. **Footer:** source, documentation, license and private security reporting. Do not add unsupported commercial comparisons or pretend Keynest runs a managed production service.

## 5. Account overview and metric definitions

### First screen

Three proposed summary cards: total items, items needing attention, and next future expiry. Attention comes before secondary charts. Card activation opens an explicit Vault filter; the next-expiry card should ultimately open the particular item, with its accessible name identifying that item. The current prototype illustrates the soon-expiring group as a destination, which is a documented approximation to refine.

Below: an actionable expiry list, an expiry distribution, a current-policy card, and recent item changes. On mobile the attention list precedes the distribution. The layout should remain useful when a category is empty or every item has no expiry.

### Exact buckets

Use one timestamp `asOf` for a response. Let `remaining = expiresAt - asOf`, and one day be 86,400 seconds for this classification. Each item belongs to exactly one bucket. Display calendar dates separately in the user's timezone; a displayed rounded day count must not determine bucket membership.

| Bucket | Predicate | Visual role |
| --- | --- | --- |
| Expired | `expiresAt <= asOf` | Error, explicit text |
| Less than 14 days | `0 < remaining < 14 days` | Attention/error, explicit text |
| 14 to less than 30 days | `14 days <= remaining < 30 days` | Yellow/attention |
| 30+ days | `remaining >= 30 days` | Green/maintenance status |
| No expiry | `expiresAt` absent | Neutral, never automatically “healthy” |

`total = sum(bucket counts)`. `needsAttention = expired + lessThan14Days`. `nextExpiry` is the smallest future expiry, not an expired date. If none exists, show “No upcoming expiry”, not zero days. Expiry is not password strength, breach exposure, present-policy compliance or external account activity.

### What exists and what needs new tracking

| Metric | Evidence / implementation need |
| --- | --- |
| Current totals and expiry groups | Derive from owner-scoped Vault item metadata; no secret reveal/decryption |
| Selected account policy | Existing account policy service |
| Recent created/updated items | `createdAt` / `updatedAt` exist in API views; expose missing frontend fields deliberately |
| Last password set | `lastRotatedAt` exists; initial creation sets it too, so do not call every value a rotation event |
| Complete item history / deleted-item history | New durable events needed; current timestamps cannot reconstruct history |
| Successful browser generations | New authenticated, best-effort tracking needed; count successful outputs, not button clicks |
| Generated passwords currently saved | New origin marker for the current saved password version needed |
| Passwords still accepted or used externally | Unknown; not available from Keynest's data |
| Historical password strength or reuse | Not an expiry-derived metric; no bulk decryption or undocumented scoring |

The current security event logger intentionally omits actor attribution and is not a durable per-user audit log. Do not repurpose server log ingestion as account history. Existing entries without origin information are **unknown**, not imported or manually created by inference. Generation counters start at an explicit tracking start date. No lifetime history may be fabricated from a recent window.

### Proposed history extension, requiring agreement

Useful events: item created, details updated, password replaced, item deleted. Optional successful-generation events support the requested counter. Store event type, owner identity, timestamp, non-secret operation ID and minimal item reference. Never store passwords, keywords, random seeds, clipboard data, password fingerprints or provider credentials for analytics.

Resolve an item label only when the item still exists and is accessible. Show “Deleted item” rather than preserving deleted labels by default. Generation provenance reported by the browser is informational, not cryptographic proof or an authorization input. Define retry/idempotency behavior so one successful save is not counted twice; generation counts are best-effort across request failures and must say so where relevant.

Discussion proposal: optional detailed history with a 30-day retention window; aggregate counts may outlive detailed events only if separately agreed and explained. Offer a clear-history control. Whether history is on by default, longer retention is desirable, aggregate counters survive clearing, and export is needed are unresolved. These are deliberate product/privacy choices, not implied consent to new telemetry. No third-party analytics or AI provider receives this data.

## 6. States, interactions and accessibility

| State | Required behavior |
| --- | --- |
| First load | Skeletons and accessible loading status, no invented zeroes |
| Empty account | Clear add-first-item path plus review-policy link |
| Populated | Accurate counts, actionable categories and useful dates |
| Initial failure | Explicit unavailable state and retry; never present failure as an empty Vault |
| Refresh failure with old data | Keep a clearly labeled earlier snapshot or show unavailable; never relabel stale data fresh |
| No attention items | Positive maintenance message with a route to Vault, not a green security guarantee |
| Expired session | Clear account state and follow existing sign-in flow; do not flash another account's data |
| Navigation/logout | Cancel pending work, discard account data and preserve existing secret concealment |

Charts have visible labels and counts. Every chart destination also has a real button/link usable by keyboard and touch; hover cannot be the only route. Status uses text plus color. Card focus indicators are visible. Prototype goals include 44px primary action targets and reflow at 320px, with tests required before compliance claims. Small reference markers in decorative preview UI are not real app controls.

Motion proposal: short 160–200ms transitions for state changes, focus/selection and panel entry. Avoid repeated number counting, autoplaying charts, parallax, or animation that shifts action targets. Respect reduced motion. Review light theme and increased text size rather than assuming the dark prototype establishes either.

Shared errors are being refined independently in #198: dismissible, temporary and paused during inline interaction. A timed notification does not remove persistent form requirements or a blocking unavailable state. New overview error states must not rely solely on a disappearing popup.

## 7. Proposed implementation and security contract

1. Review the composition and wording with the maintainer at desktop and phone widths. Agree on the scope and retention of history before implementing tracking.
2. Implement public home and signed-in entry in Angular, preserving dedicated routes and authorized deep-link return. Keep account state out of prerendered public content and static caches.
3. Add an authenticated owner-scoped overview query for bounded counts, earliest future expiry, a small attention list, effective policy and recent metadata. Return one `asOf` plus `Cache-Control: no-store`. Derive identity from the session, never a client-supplied owner ID. No secret decryption, cross-user aggregation, N+1 lookups or complete Vault downloads just for a chart. Review owner/expiry/time indexes with actual query evidence.
4. Add dedicated Vault filters with the same bucket definitions. On selection, make the filter visible and preserve it through safe navigation. Counts and filtered lists must reconcile against the same semantics, with concurrent updates handled as refreshed snapshots.
5. Add agreed origin tracking and account history in a separate reviewed backend migration/PR. Emit mutation events only after successful, owner-authorized operations; failed writes must not leave success history. Do not change ciphertext/AAD, key custody or password registration policy for analytics.
6. Verify cross-account denial, no-store, bucket edges, retries, cancelled requests, session changes, safe errors, light/dark responsive behavior, keyboard, reduced motion, empty/error recovery and migration/retention behavior. Use synthetic data. Record the exact human-tested revision and environment.

No first-party cloud dependency or AI call is needed for any dashboard calculation. Full-history and generation tracking can be delivered as part of the complete feature once their contract is agreed, rather than presented prematurely as existing metrics.

## 8. Review decisions

- Composition: the proposed editorial public page and operational account layout, including desktop rail and existing mobile horizontal navigation.
- Overview priorities: totals, attention, next expiry, distribution, policy and recent changes; decide if any should be removed or reordered.
- History: scope, opt-in/default behavior, retention, clearing semantics and aggregate lifetime.
- Generation provenance: whether an approximate generation counter is useful enough to justify tracking, and the label “Keynest-generated passwords currently saved”.
- Deployment entry: whether sign-in or account creation leads the primary action for an installed instance; the public site must not imply a hosted Keynest service exists.

Acceptance requires reviewed visual states and meaningful navigation, accurate metrics, preserved security boundaries, passing CI, and a maintainer walkthrough. Merging this proposed design document is not approval to merge the production feature.

## 9. Verification of this proposal

Local JavaScript syntax and documentation/asset links were checked. A DOM-level check exercised public/account navigation round trips, attention and all five expiry filters (2/3/5/12/2 sample items), state selection, modal controls, the phone-width toggle and unique element IDs, without execution errors. This is logic verification, not pixel rendering, native dialog focus verification, a browser accessibility audit or a human Edge walkthrough.

The cloud browser could not open the workspace preview; no completed browser visual review is claimed. Use the standalone HTML for visual review in Edge at desktop, phone and increased text sizes before approving production implementation. No cloud-browser restriction was bypassed to inspect it.
