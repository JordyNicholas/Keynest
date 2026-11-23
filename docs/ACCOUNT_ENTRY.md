# Account entry and reference navigation

Status: implementation follow-up for #220; requires the normal feature PR review and Windows/Edge walkthrough. Related tailored-design verification remains open in #199.

## Credential wording

The web form calls the authentication credential **Keynest password**. It signs in to this Keynest instance; it is distinct from the account passwords stored as Vault items. Existing registration still requires at least 15 Unicode code points. Sign-in does not retroactively apply that registration minimum to an existing credential.

“Master password” is common terminology for a password manager's primary unlock credential. In Keynest's current server-managed encryption architecture, the account password is verified through Argon2id for authentication; it is not the input that derives the server Vault key. The clearer UI wording avoids implying that relationship. Backend payload names, hashing, session transport, keys and stored-data recovery are unchanged. See ADR-0006 and ADR-0007 for those contracts.

## Dialog and external references

Account entry uses a scoped 18px backdrop blur and dark scrim. A more opaque scrim is the fallback when backdrop filtering is unsupported. The dialog surface itself stays sharp; focus trapping, cancellation, credential cleanup and focus restoration remain with the existing Material dialog. Reduced-motion preference removes the scrim transition.

GitHub links from the homepage, account help and workspace open a separate browsing context with `target="_blank"` and `rel="noopener noreferrer"`. A visible external-link arrow and screen-reader text identify that behavior. The browser/user ultimately controls whether the context is a tab or window. Internal section navigation continues in Keynest with its existing smooth scrolling/reduced-motion behavior.

## Focused verification

- Homepage section anchors stay in the current tab; GitHub source, security, setup, recovery and license references open separately.
- Account Help and documentation and the workspace Security model reference behave the same way.
- Sign-in and signup show the Keynest-password label and appropriate helper/validation text, with password masking and correct autocomplete.
- Opening the dialog obscures background text while leaving dialog content readable at narrow widths and browser zoom; test blur fallback if available.
- Escape/Close cancel pending attempts and clear credentials; keyboard focus remains trapped until closing and returns to its trigger.

Automated route/form tests cover the reference and dialog contracts. Actual mobile/desktop visual checks in Windows/Edge remain human verification, not inferred from a successful build.
