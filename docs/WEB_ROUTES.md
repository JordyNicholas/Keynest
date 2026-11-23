# Web access and public generation

The landing page is the only anonymous web page. Authentication opens explicitly in its dialog (`/?auth=signin` or `/?auth=signup`). Access attempts to workspace URLs return signed-out visitors to `/`, without opening a dialog, mounting workspace content, or making workspace requests. Clearing the session unmounts the workspace and returns to `/`.

| Route | Purpose | Requires tab session |
| --- | --- | --- |
| `/`, `/#generator`, `/#how`, `/#security`, `/#documentation` | Public product information and fixed-policy local generation | No |
| `/overview` | Owner-scoped summary and expiry links | Yes |
| `/vault` | Owner-scoped search and item actions | Yes |
| `/vault/new` | Create an owned item | Yes |
| `/vault/edit/:itemId` | Edit an owned item | Yes |
| `/vault/rotate/:itemId` | Replace an owned password | Yes |
| `/tools`, `/tools?tool=generate#generate`, `/tools?tool=check#check` | Account-policy tools | Yes |
| `/account` and its settings/diagnostics fragments | Profile, policy, AI setup and connection diagnostics | Yes |
| Any other path | Redirect to `/` (signed-in users then reach `/overview`) | No content |

`/account` and `/tools` remain active navigation destinations. The standalone Account login form and temporary anonymous checker controls are removed. The old `/tools/generate`, `/tools/check`, `/status` and `/policy-explanation` aliases are removed; application links use canonical destinations. Account entry remains the shared homepage dialog. Explicit safe dialog return links remain supported, but unauthorized path attempts do not preserve a destination.

The public generator imports the canonical packaged `keynest.default.v1` policy and uses the existing Web Crypto browser generator. It generates 20 characters with all four categories; it offers no policy editor, keyword, save action or API call. The generated password is transient component/DOM state, cleared on Clear, page/tab hide or destruction. Copy writes to the system clipboard only on request. There is no storage/history/telemetry of the generated value; JavaScript strings and the user's clipboard cannot be securely erased by the app.

The shared Angular guard controls UI entry using the existing tab-session state. It does not grant API authorization: the server continues checking session validity and item ownership on every data operation. No backend endpoint, token storage, encryption, or database contract changes here.
