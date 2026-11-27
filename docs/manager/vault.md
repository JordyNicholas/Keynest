# Vault items and expiry

Open **Vault** to search, filter and manage your saved logins.

## Add, edit, rotate, or delete

- **Add Vault item** saves an existing password or generates one using your account policy. The label identifies the item. Username, website, and expiry are optional.
- Expand a card and choose **Reveal** to see its password for 15 seconds. **Conceal** hides it sooner. Closing the card, navigating away, or signing out clears the displayed value.
- Use **Edit details** to change metadata. Use **Rotate** to save a replacement after changing the password at the external service.
- **Delete** requires confirmation. Use [Backup and restore](backup-and-restore.md) to keep portable copies of selected items.

## Manager Web browsing

The Vault uses the [bounded search endpoint](../api/vault-browsing.md#search-page) for listing. Search is debounced 250ms and lives in memory/request bodies; changing the text, field, sort, expiry group, page or size closes expanded content, clears secrets/timers and cancels stale reads. The URL carries only the expiry group and optional item UUID (`/vault?expiry=upcoming&item=<uuid>`), so overview links can target a real filtered view. Clearing search retains the expiry filter. Whole-Vault expiry counts are labelled separately from matching results.

One Angular Material expansion panel opens at a time. Its header contains label/domain/expiry only; reveal, edit, rotate and confirmed delete are inside. Expansion itself never reveals a password. Collapsed content becomes inert. Explicit reveals remain POST/no-store and conceal after 15 seconds, with a visible countdown. Collapse/navigation/session changes cancel pending reveals and discard displayed values. Server-side completion cannot be undone merely by cancelling a browser subscription.

Loading, first-use, filtered-empty, selected-item-unavailable and persistent failure/retry states are distinct. Deletion refreshes counts and lets the server clamp the last page. The former duplicate upcoming-expiry list is replaced by explicit expiry filters; per-item notification lead time remains visible metadata, separate from the fixed overview buckets.

Manual Windows/Edge checks: browse each page size; combine label/URL/username queries with every expiry group; clear only search; follow/refresh a selected-item URL; navigate headers with keyboard and open one panel; reveal/collapse and wait for the countdown; change page/filter during a pending request; delete the last item on a page; verify 320px/enlarged-text reflow, action alignment, reduced motion, empty/failure recovery and logout concealment. Browser unit tests and a build do not replace that walkthrough.

## Validation follow-up: visual controls (#222)

Semantic expiry colors are shared with the overview: red for expired/less than 14 days and the aggregate attention group, amber for 14–29 days, green for 30+ days, and neutral for no expiry/all-items controls. Labels, counts, icons and aria-pressed selection remain present; color does not replace the meaning. The exact duration buckets and “expiry is not strength” explanation are unchanged.

The page footer combines a compact numbered paginator and the existing 10/25/30/50 size choices. It shows a bounded number window with ellipses/edge pages on wider containers and three nearby numbers with previous/next at mobile widths. The current number has aria-current, inaccessible directions are disabled, repeated current-page clicks do not request data, and successful page changes focus the results heading. No unbounded list of page buttons is generated.

Item actions use local decorative SVGs, shorter labels, consistent touch targets and a two-column compact/four-column wide layout, falling back to one column in especially narrow content. Delete has explicit text, a destructive color and its existing confirmation; reveal/conceal, editing and rotation keep their original behavior. Timed reveals retain their countdown and lifetime. No icon font or network icon service is required.

Focused browser tests exercise bounded pagination, current/disabled states, small-container reflow, page-result focus, labels and action layout/behavior. See [supplementary platform verification](https://github.com/JordyNicholas/Keynest/issues/268) for the Windows/Edge walkthrough.

## Vault expiry date entry

Users can type a numeric date or open the calendar and navigate months/years. Browser locale determines the initial Gregorian date order and display; the format control offers DD/MM/YYYY, MM/DD/YYYY or YYYY-MM-DD explicitly. The field shows its current pattern. Numeric slash, dot, dash and whitespace separators are accepted with that order; years require four digits. Invalid and incomplete dates are rejected without Date.parse normalization. Ambiguous numeric dates follow the chosen order, never an inferred fallback. Calendar month/day names use the browser locale; explicit order choices change the input format, not application language. No date-format preference is persisted.

A selected date expires at 23:59:59.999 in the browser's local timezone. The client sends its ISO UTC instant, so selection is not approximated as a number of 24-hour days. Default new-item selection is 90 local calendar days ahead. Clearing the create field creates an item without expiry. On edit/rotation, the current expiry is displayed and the calendar starts there; leaving the new-date field blank preserves the original instant. This avoids changing an existing expiry time during an unrelated metadata edit. Past choices and dates beyond the supported year range are invalid. Notification lead time remains a number of days before expiry.

## Additive API contract

POST/PUT Vault item bodies accept optional `expiresAt`, an ISO-8601 instant (an explicit numeric timezone offset is also accepted). The API rejects a past absolute expiry, an unsupported calendar range, or supplying both `expiresAt` and `expiresInDays` before saving changes. Owner checks remain unchanged. `expiresAt: null`/omission means no expiry on create and keep existing expiry on update; this does not add an expiry-clearing mutation for existing items.

Existing `expiresInDays` clients and Java request constructors retain their prior behavior, including relative-date fixtures for expired items. The database already stores an Instant; no schema, encryption, export-payload, reminder-processing or session migration is needed. The frontend routes for create/edit/rotation load lazily so the picker is not part of public landing-page startup.

### Verification and rollback

Tests cover locale/explicit formats, leap-day and rollover rejection, manual entry and Material calendar selection, local-day/ISO conversion, invalid-input save refusal, exact authenticated request payloads, preservation on metadata updates and existing relative API compatibility. API negatives cover conflicts, malformed/past/out-of-range dates and unchanged records after rejection. Run the normal Angular builds/tests, Maven matrix and packaged/hosted PostgreSQL smoke.

Before closing the human UI gate, check Windows/Edge manual entry and calendar navigation with pt-BR and en-US, focus/keyboard, reduced motion and narrow screens. Automated tests do not replace that walkthrough. Reverting the UI/API slice requires no data migration; already stored absolute expiry instants remain valid ordinary Vault metadata.

If you try to save a login already present with the same username, password and website, the form shows “This login is already saved in your Vault.” Other fields remain available to edit and the password input is cleared. A missing website matches another missing website; restore still supports its separately reviewed Keep both choice.
