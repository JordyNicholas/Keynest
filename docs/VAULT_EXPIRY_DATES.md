# Vault expiry date entry

The create, edit and password-rotation forms use Angular Material's datepicker, following the keyboard/calendar interaction requested with the MUI Date Picker reference. MUI's React package is not added to the Angular application.

Users can type a numeric date or open the calendar and navigate months/years. Browser locale determines the initial Gregorian date order and display; the format control offers DD/MM/YYYY, MM/DD/YYYY or YYYY-MM-DD explicitly. The field shows its current pattern. Numeric slash, dot, dash and whitespace separators are accepted with that order; years require four digits. Invalid and incomplete dates are rejected without Date.parse normalization. Ambiguous numeric dates follow the chosen order, never an inferred fallback. Calendar month/day names use the browser locale; explicit order choices change the input format, not application language. No date-format preference is persisted.

A selected date expires at 23:59:59.999 in the browser's local timezone. The client sends its ISO UTC instant, so selection is not approximated as a number of 24-hour days. Default new-item selection is 90 local calendar days ahead. Clearing the create field creates an item without expiry. On edit/rotation, the current expiry is displayed and the calendar starts there; leaving the new-date field blank preserves the original instant. This avoids changing an existing expiry time during an unrelated metadata edit. Past choices and dates beyond the supported year range are invalid. Notification lead time remains a number of days before expiry.

## Additive API contract

POST/PUT Vault item bodies accept optional `expiresAt`, an ISO-8601 instant (an explicit numeric timezone offset is also accepted). The API rejects a past absolute expiry, an unsupported calendar range, or supplying both `expiresAt` and `expiresInDays` before saving changes. Owner checks remain unchanged. `expiresAt: null`/omission means no expiry on create and keep existing expiry on update; this does not add an expiry-clearing mutation for existing items.

Existing `expiresInDays` clients and Java request constructors retain their prior behavior, including relative-date fixtures for expired items. The database already stores an Instant; no schema, encryption, export-payload, reminder-processing or session migration is needed. The frontend routes for create/edit/rotation load lazily so the picker is not part of public landing-page startup.

## Verification and rollback

Tests cover locale/explicit formats, leap-day and rollover rejection, manual entry and Material calendar selection, local-day/ISO conversion, invalid-input save refusal, exact authenticated request payloads, preservation on metadata updates and existing relative API compatibility. API negatives cover conflicts, malformed/past/out-of-range dates and unchanged records after rejection. Run the normal Angular builds/tests, Maven matrix and packaged/hosted PostgreSQL smoke.

Before closing the human UI gate, check Windows/Edge manual entry and calendar navigation with pt-BR and en-US, focus/keyboard, reduced motion and narrow screens. Automated tests do not replace that walkthrough. Reverting the UI/API slice requires no data migration; already stored absolute expiry instants remain valid ordinary Vault metadata.
