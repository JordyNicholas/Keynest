# Keynest idea catalog

**Status:** proposed. Idea intake closed November 24, 2026 (America/Sao_Paulo). Interest and the map below are a review of that set. They are not approval to build.
**Captured:** November 24, 2026 (America/Sao_Paulo). Later the same day: credential kinds, companion and component meeting locally, violation fixtures, grant receipts, and the research notes below.
**Format:** [FORMAT.md](FORMAT.md)

This catalog collects the ideas from the November 24 product conversation. It is not a release commitment. The [canonical roadmap](../../contributing/project/roadmap.md) and accepted ADRs still govern what is in scope. Nothing here is shipped.

## Standing constraints

These apply to every open idea.

1. **The end user decides.** A model, an embedding site, or a Keynest operator can prepare a draft. Applying a policy, issuing a grant, or saving a secret happens only after the end user confirms that action.
2. **The end user can see the basis of the decision.** Show the rule conflict, the grant fields, or the violation codes. Hide the provider key and the vault secret.
3. **The model never grants permission.** Schema checks, the policy engine, and server-side authorization remain the authority. AI stays optional.
4. **Inferences stay with the caller.** BYOK calls use the end user's provider credential. A Keynest-operated service does not receive a copy of the prompt or the completion. An embedding site does not receive the end user's agent output unless that user sends it.
5. **Secrets stay out of the model.** No vault password, backup passphrase, session bearer, or provider key enters a prompt, a log, or a messenger.
6. **Grant approval copy is a template.** The sentence is filled from the bound origin, client, lifetime, and single-use flag. A paraphrase is not used there.
7. **The browser reads the page the person already opened.** Keynest's server does not fetch an arbitrary URL on the user's behalf.
8. **A messenger is a doorbell.** Email, WhatsApp, and Telegram may carry "open Keynest to approve." They do not carry a password or a grant.

## Design order already agreed

Design passes, after this catalog is evaluated, run in this order:

| Order | Idea | What the pass covers |
| --- | --- | --- |
| 1 | [IDEA-005](#idea-005--browser-companion) | The companion, the text-to-policy interpreter, and the vault checks the companion needs |
| 2 | [IDEA-008](#idea-008--distributed-policy-tool-and-themeable-ui) | Policy as a tool a marketplace can embed, with the end user's own agent kept private |
| 3 | [IDEA-009](#idea-009--single-use-access-workflows) | Validating a credential and issuing a single-use grant for an opted-in service |

Order is a sequencing decision. It is not the interest map. The map waits until brainstorming is closed.

## Observed gaps

Notes from public guidance and industry practice on November 24, 2026. They explain later ideas. They are not a claim that Keynest implements any of them.

- NIST SP 800-63B-4 tells verifiers not to impose composition rules, not to require periodic password changes, to accept long passwords, and to reject a candidate that appears on a blocklist of common, expected, or compromised values. Keynest's current policies are composition rules plus optional expiry, because that is what many websites still publish. The companion exists to handle that clash. A length-and-blocklist preset is the other side of it. Source: [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html).
- Storing a TOTP seed in the same vault item as the password means one unlocked vault yields both factors. Privacy Guides and several operator write-ups treat that as a real loss of factor independence. Recovery codes have the same concentration problem, and people often keep them in screenshots. Sources: [Privacy Guides](https://www.privacyguides.org/en/basics/passwords-overview/), [ISE](https://blog.securityevaluators.com/psa-dont-store-2fa-codes-in-password-managers-77d92608b062).
- Passkeys remove the shared password on sites that offer them. The remaining hard parts are sharing, revocation, and recovery. A 2025 systematization of E2EE recovery found recovery codes are the common backup and that people lose them. Keynest does not need to become a passkey provider to be useful there. Sources: [OWASP passkey cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Passkey_Security_Cheat_Sheet.html), [PoPETs 2025 SoK](https://petsymposium.org/popets/2025/popets-2025-0113.pdf).
- Credentials leave through the clipboard into a chat box. In August 2025, researchers showed a poisoned document could make a ChatGPT connector search a connected drive for API keys and embed them in an image URL. OpenAI shipped a mitigation. The everyday path is still paste. Sources: [WIRED, August 6, 2025](https://www.wired.com/story/poisoned-document-could-leak-secret-data-chatgpt/), [Zenity AgentFlayer](https://zenitylabs.beehiiv.com/p/agentflayer-chatgpt-connectors-0click-attack-5b41).

## Index

| Id | Title | Status | Sequence | Interest |
| --- | --- | --- | --- | --- |
| IDEA-001 | Rules check | captured | support for 1 | high |
| IDEA-002 | Policy impact preview | captured | support for 1 | high |
| IDEA-003 | Queue narrator | captured | support for 1 | medium |
| IDEA-004 | Text-to-policy interpreter | captured | support for 1 | high |
| IDEA-005 | Browser companion | selected | 1 | high |
| IDEA-006 | Per-site policy | captured | support for 1 | high |
| IDEA-007 | Policy drift report | captured | | medium |
| IDEA-008 | Distributed policy tool and themeable UI | selected | 2 | high |
| IDEA-009 | Single-use access workflows | selected | 3 | high |
| IDEA-010 | Grant-policy draft | captured | support for 3 | medium |
| IDEA-011 | Password-store retirement checklist | captured | support for 3 | medium |
| IDEA-012 | Refusal shown on the service's screen | captured | support for 3 | medium |
| IDEA-013 | Policy packs | captured | | medium |
| IDEA-014 | CI policy check | captured | | medium |
| IDEA-015 | Staged rotation | captured | | medium |
| IDEA-016 | Read-only agent status | captured | | low |
| IDEA-017 | Companion and embedded policy meet locally | captured | support for 1 and 2 | high |
| IDEA-018 | Violation fixtures | captured | support for 2 | high |
| IDEA-019 | Grant receipt and revoke | captured | support for 3 | high |
| IDEA-020 | Policy per credential kind | captured | | high |
| IDEA-021 | Factor separation | captured | support for 20 | high |
| IDEA-022 | Length-and-blocklist policy, calendar rotation off | captured | | high |
| IDEA-023 | Local paste guard | captured | | medium |
| IDEA-024 | Service asks for a phishing-resistant authenticator | captured | support for 3 | medium |
| IDEA-R01 | Session password delivered to the user | rejected | | |
| IDEA-R02 | Model approves or relaxes a rule | rejected | | |
| IDEA-R03 | Model reads or fills the password field | rejected | | |
| IDEA-R04 | Chat on the vault or the login page | rejected | | |
| IDEA-R05 | Server fetches any URL the user pastes | rejected | | |
| IDEA-R06 | Narration as the whole AI product | rejected | | |
| IDEA-R07 | Keynest logs into sites that have not opted in | rejected | | |

## Evaluation

Intake is **closed**. Scores use the test below. High means the idea should be designed. Medium means it helps a chosen design or can wait. Low means the vault already answers the question.

The test, in order:

1. The end user has a concrete stuck moment, and they remain the one who decides.
2. The deterministic core still helps them with AI disabled.
3. Comparable password managers or identity products do not already do this.
4. The integration is a vault screen, a browser companion, a library, a themeable component, or an opted-in app.
5. No new cipher, and no secret in the model.

| Design now | Keep as support | Hold | Reject |
| --- | --- | --- | --- |
| [IDEA-005](#idea-005--browser-companion) is the first design document. [IDEA-001](#idea-001--rules-check), [IDEA-002](#idea-002--policy-impact-preview), and [IDEA-004](#idea-004--text-to-policy-interpreter) are part of that pass. | [IDEA-007](#idea-007--policy-drift-report) and [IDEA-017](#idea-017--companion-and-embedded-policy-meet-locally) sit on the companion. [IDEA-003](#idea-003--queue-narrator) is optional wording. | [IDEA-006](#idea-006--per-site-policy) waits until the companion shows a real conflict, and it needs an ADR. | [IDEA-R01](#idea-r01--session-password-delivered-to-the-user) through [IDEA-R07](#idea-r07--keynest-logs-into-sites-that-have-not-opted-in) |
| [IDEA-020](#idea-020--policy-per-credential-kind) is the schema slice. It needs no model. [IDEA-021](#idea-021--factor-separation) and [IDEA-022](#idea-022--length-and-blocklist-policy-calendar-rotation-off) belong to that slice. | [IDEA-018](#idea-018--violation-fixtures) proves a policy. It can ship with the library before the themeable component. | [IDEA-013](#idea-013--policy-packs), [IDEA-014](#idea-014--ci-policy-check), [IDEA-015](#idea-015--staged-rotation), [IDEA-023](#idea-023--local-paste-guard) | |
| [IDEA-008](#idea-008--distributed-policy-tool-and-themeable-ui) is the second design, then [IDEA-009](#idea-009--single-use-access-workflows). | [IDEA-010](#idea-010--grant-policy-draft), [IDEA-012](#idea-012--refusal-shown-on-the-services-screen), [IDEA-019](#idea-019--grant-receipt-and-revoke), [IDEA-024](#idea-024--service-asks-for-a-phishing-resistant-authenticator) | [IDEA-011](#idea-011--password-store-retirement-checklist) until a fixed checklist is not enough. [IDEA-016](#idea-016--read-only-agent-status) until the agent ADR. | |

The first written design, when one is requested, is the browser companion. Credential kinds can be specified as a library change beside it because validation does not wait on a model or on the browser.

## How to add an idea

Copy the field list from [FORMAT.md](FORMAT.md). Use the next free `IDEA-NNN`. Link parents. Leave Interest as `unrated`. Add a row to the index. If the idea breaks a standing constraint, put it in Open questions or mark it rejected.

---

## IDEA-001 — Rules check

- **Status:** captured
- **Interest:** high. The vault cannot say which saved passwords fail the rules now in force.
- **Sequence:** support for 1
- **Parents:** none
- **Origin:** Product review, November 24, 2026

### Problem

Changing an account policy leaves existing logins sealed under the previous policy id. The vault does not say which saved passwords fail the rules in force now.

### Proposal

A signed-in re-check opens each owned envelope, runs `PasswordValidator` against the current account policy, and returns violation codes such as `password.too_short`. The plaintext is discarded. Overview lists the failing logins and links each one to rotate.

### End-user decision

The user chooses when to run the check and which login to rotate. The check does not change a secret.

### Visibility

The signed-in user sees their own codes, labels, and hosts. Another tenant sees nothing. The model is not called.

### Model may see

none

### Model must not see

Passwords, denylist entries, envelopes, session bearers.

### Deterministic core

`PasswordValidator` and owner-scoped vault reads. The feature is complete with AI disabled.

### Integration

Vault UI, on the overview.

### Distinctiveness

The result is pass or fail against the same policy document used to generate passwords, not a strength score.

### Dependencies

Current account policy (ADR-0011) and seal open. Re-authentication and admission follow the export pattern so a bulk open is an explicit act.

### Open questions

none

## IDEA-002 — Policy impact preview

- **Status:** captured
- **Interest:** high. Saving a stricter policy without a count is a blind decision.
- **Sequence:** support for 1
- **Parents:** IDEA-001
- **Origin:** Product review, November 24, 2026

### Problem

Saving a stricter policy is a blind bet. The user cannot see how many current logins would start failing.

### Proposal

An unsaved draft is checked with the same open-and-validate path. The account screen shows the count and the code histogram before save.

### End-user decision

Saving the draft uses the existing account-policy action. The preview never saves itself.

### Visibility

Same as the rules check. The draft stays on the user's account.

### Model may see

none

### Model must not see

The opened passwords and the denylist contents.

### Deterministic core

Schema validation plus `PasswordValidator` against the draft. User-supplied regular expressions stay rejected.

### Integration

Account policy screen.

### Distinctiveness

The preview measures failure against an enforceable document, before that document is saved.

### Dependencies

IDEA-001

### Open questions

none

## IDEA-003 — Queue narrator

- **Status:** captured
- **Interest:** medium. The sort already ranks the queue. The paragraph is optional.
- **Sequence:** support for 1
- **Parents:** IDEA-001
- **Origin:** Product review, November 24, 2026

### Problem

A list of codes tells a careful user what failed. It does not help them decide where to start.

### Proposal

The list is sorted without a model: overdue first, then by code severity. An optional BYOK call writes a short plan from counts, codes, and expiry buckets.

### End-user decision

The user asks for the paragraph. Rotation still requires them to open each login and replace the secret.

### Visibility

The user sees the paragraph. Host names enter the prompt only if the user includes them. Keynest as an operator does not keep the completion.

### Model may see

Counts, violation codes, expiry buckets, and hosts the user opts in.

### Model must not see

Passwords, denylist strings, usernames, envelopes, provider keys.

### Deterministic core

The sorted queue from IDEA-001. The paragraph is optional.

### Integration

Overview, beside the rules-check list.

### Distinctiveness

The plan is an explanation of engine codes. It is not a risk score and it cannot reorder the vault.

### Dependencies

IDEA-001. Ephemeral BYOK, as in ADR-0010.

### Open questions

Whether host names are worth including at all. The default in this record is to leave them out.

## IDEA-004 — Text-to-policy interpreter

- **Status:** captured
- **Interest:** high. Prose has to become schema-valid JSON before the engine can enforce it.
- **Sequence:** support for 1
- **Parents:** none
- **Origin:** AI brainstorm, November 24, 2026

### Problem

Password rules are written as prose on signup pages, change-password forms, and internal standards. The engine can enforce a policy only after that prose becomes schema-valid JSON.

### Proposal

The model proposes policy JSON limited to the fields account policy already allows: length, categories, ambiguous characters, whitespace, username handling, and blocked strings. The server, or the caller running the library, schema-checks the JSON and rejects regular expressions and unknown fields.

### End-user decision

The user reviews the diff and chooses to keep, edit, or discard the draft. Nothing is saved by the model.

### Visibility

The user sees the prose they submitted and the draft. Their provider sees the request. Keynest as an operator does not.

### Model may see

The public sentences the user confirmed.

### Model must not see

Password fields, current secrets, the user's other vault items, provider keys.

### Deterministic core

The JSON Schema and `PasswordValidator`. With AI off, the user can still set the same fields by hand.

### Integration

Called by the browser companion (IDEA-005) and, later, by the embeddable component (IDEA-008).

### Distinctiveness

The output is an enforceable Keynest policy, not advice and not a strength score.

### Dependencies

`keynest-policy` schema. ADR-0011 field limits.

### Open questions

none

## IDEA-005 — Browser companion

- **Status:** selected
- **Interest:** high. This is the first design. A site's written rules and the account policy clash on the page.
- **Sequence:** 1
- **Parents:** IDEA-004
- **Origin:** AI brainstorm, November 24, 2026. Chosen as the first design target.

### Problem

On a site Keynest does not operate, the person learns the site's rules only after a generated password is rejected. Their account policy and the page can contradict each other, for example a minimum of 16 against a maximum of 12.

### Proposal

A browser companion reads the requirement text on the page the user already has open. The user confirms the extracted sentences. The interpreter drafts a policy. The engine intersects that draft with the account policy. A conflict stops generation and names the clashing fields. When both sides allow a password, Keynest's generator creates it.

### End-user decision

The user confirms the extracted sentences, accepts or rejects the draft, and chooses whether a generated password is saved or filled. The companion does not submit the site's form.

### Visibility

The user sees the extract, the conflict, and the draft. The page's password field is not uploaded. The site does not receive the agent completion.

### Model may see

Confirmed requirement sentences.

### Model must not see

The password field, the current password, vault rows, provider keys.

### Deterministic core

Intersection of two policies and generation through `keynest-generate`. Hand-set constraints still work with AI disabled. The rules check (IDEA-001) and impact preview (IDEA-002) are the vault side of the same pass.

### Integration

Browser companion on sites that will never call Keynest. A deep link from the rules-check queue opens the companion with the failing codes.

### Distinctiveness

Password managers fill and score. They do not turn the sentence on the page into a policy and refuse to generate when the account rules and the site rules cannot both be met.

### Dependencies

IDEA-001, IDEA-002, IDEA-004. Per-site save is IDEA-006 and can follow after the conflict is visible.

### Open questions

The smallest companion that can ship before a full extension: a page-local action the user invokes, versus an always-on extension. The design pass answers this. Filling stays out of the first design unless the user asks for it.

## IDEA-006 — Per-site policy

- **Status:** captured
- **Interest:** high. One account policy cannot satisfy every site. It waits on an ADR and on a conflict the companion has shown.
- **Sequence:** support for 1
- **Parents:** IDEA-005
- **Origin:** Product review, November 24, 2026

### Problem

One account policy cannot satisfy every site. A bank that allows only 8 characters and no symbols would otherwise lower the rule for the whole vault, or make generation fail on that site forever.

### Proposal

A site can keep its own current policy id. New and rotated secrets for that site seal under that id. The account default stays stricter. The ciphertext context already binds a policy id per item.

### End-user decision

The user saves the site policy from a reviewed draft. A stale id continues to return HTTP 409 until this idea has its own ADR.

### Visibility

The site policy is account metadata, visible to the signed-in user. It is not sent to the model except as the scalar fields already allowed for explanation.

### Model may see

none, unless a later explanation is asked for. That explanation uses the scalar projection, not the denylist.

### Model must not see

Banned strings, passwords, other sites' secrets.

### Deterministic core

ADR-0011 already resolves one current account policy and rejects a stale id. This idea adds named, still-current site policies. The engine enforces whichever id was saved.

### Integration

Vault item create and rotate, fed by the browser companion when a conflict is saved as a site exception.

### Distinctiveness

The exception is a versioned policy document bound into the seal context, not a one-off generator setting.

### Dependencies

A new ADR. IDEA-005 shows the conflict that justifies it. Do not design this before that conflict exists in the companion.

### Open questions

How a site is identified: registrable domain, full origin, or the user-supplied label.

## IDEA-007 — Policy drift report

- **Status:** captured
- **Interest:** medium. Provenance beside the verdict. Small once the rules check exists.
- **Sequence:**
- **Parents:** IDEA-001
- **Origin:** Product review, November 24, 2026

### Problem

Items remember the policy id they were sealed with. That id is provenance. Users can mistake it for proof that the password fails the current rules.

### Proposal

Show the sealed policy id next to the rules-check verdict. The verdict comes from validation. The id explains which document was current when the secret was saved.

### End-user decision

none beyond reading the report.

### Visibility

Signed-in user only.

### Model may see

none

### Model must not see

Passwords.

### Deterministic core

Metadata `policyId` plus IDEA-001 codes.

### Integration

Vault list and the rules-check row.

### Distinctiveness

Provenance and verdict are separate. A strength meter collapses them into one number.

### Dependencies

IDEA-001. Shipping the badge without the verdict would mislead, so this waits for the check.

### Open questions

none

## IDEA-008 — Distributed policy tool and themeable UI

- **Status:** selected
- **Interest:** high. Second design. A service should enforce its own standard without a Keynest-hosted assistant.
- **Sequence:** 2
- **Parents:** IDEA-004
- **Origin:** Maintainer, November 24, 2026. Second design target.

### Problem

A marketplace, or any service that sets password standards, has to rebuild those rules in its own signup form. Keynest Policy was conceived as something that service could run itself.

### Proposal

Ship `keynest-policy` and a UI component as separate artifacts that also work together. The service embeds the component, applies its own theme, and defines the standard it will enforce. The component calls the library. An optional BYOK agent, the end user's own, may propose a change to that user's policy. The completion stays with the user. Keynest does not receive the inference.

### End-user decision

The end user confirms any change the agent proposes to their policy. The embedding service decides the standard it requires of its own accounts. The component does not let the agent override the service's standard, and it does not let the service apply a change to the user's personal policy.

### Visibility

The service sees its own policy configuration and the pass or fail result of a candidate it is allowed to check. The service does not see the end user's agent prompt or completion. Keynest as an operator sees neither.

### Model may see

Only what the end user's own client sends to their provider: public rule text they chose to interpret.

### Model must not see

The marketplace's user database, the candidate password, and any credential of the service.

### Deterministic core

`keynest-policy` validates and explains rules without a model. The component must be usable with no AI configured.

### Integration

Library for the service's backend. Themeable UI component on the service's account and signup screens. The two ship separately and can be used separately.

### Distinctiveness

The service themes and operates the control. The end user's agent is not a Keynest-hosted assistant, and the vendor does not see its inferences.

### Dependencies

IDEA-004 for the optional draft path. Current `keynest-policy` library. A component contract that carries no Keynest account session unless the end user explicitly connects one.

### Open questions

Whose policy a single screen is editing when both the marketplace standard and the person's Keynest policy are present. The record above keeps them separate until the design pass. Also unresolved: web component versus a framework-specific widget, and how theming is expressed.

## IDEA-009 — Single-use access workflows

- **Status:** selected
- **Interest:** high. Third design. A service that opts in should receive a single-use grant, not a password the person carries.
- **Sequence:** 3
- **Parents:** none
- **Origin:** Maintainer, November 24, 2026, refined in the same conversation. Third design target.

### Problem

A reusable password is a poor fit for a service that can ask Keynest whether this person may start a session. The person also should not have to carry a new password from a messenger into that service.

### Proposal

Keynest is a credential manager and a validator for services that opt in. The service asks this person's Keynest instance to validate the request. The person approves that exact request in an unlocked Keynest session. Keynest then issues a unique single-use grant to the service, bound to client, origin, nonce, and expiry. The service stores a session. It does not store a reusable password. The original suggestion that the password travel by email, WhatsApp, or Telegram is rejected in IDEA-R01. Those channels may only ask the person to open Keynest.

### End-user decision

The user approves or denies the specific grant. They choose which channel may notify them. They choose which of their services are registered. A model cannot approve.

### Visibility

The user sees the bound fields: app, origin, lifetime, single use. The service sees the grant result. A messenger sees a request id and a deep link. Keynest as an operator of someone else's instance is not part of this flow; the person's own instance is the authority.

### Model may see

none for the approval itself. IDEA-010 and IDEA-012 are optional attachments and have their own limits.

### Model must not see

The grant secret, the master password, vault passwords, provider keys.

### Deterministic core

The person's Keynest session validates the user. The grant is single-use, origin-bound, and rejected on replay or expiry. Signing keys for grants are separate from the vault encryption key. Approval text is the template in standing constraint 6.

### Integration

A small library the opted-in service calls. Manager is the reference issuer. This is the embeddable form of the workflow, matching the library posture of IDEA-008.

### Distinctiveness

Identity products already run an approval ceremony. This one uses the same policy document as stored passwords: lifetime, single use, allowed origins, and whether a reusable password is forbidden. The service can verify grants with the libraries.

### Dependencies

A new ADR for the grant format, signing keys, and availability. IDEA-010, IDEA-011, and IDEA-012 belong to this design pass and do not start before it.

### Open questions

The first slice is one demo service, approval on the Keynest screen, and tests for replay and wrong origin. Messenger doorbells wait until that slice exists. Consumer sites that have not registered, including LinkedIn, are out of this idea (IDEA-R07).

## IDEA-010 — Grant-policy draft

- **Status:** captured
- **Interest:** medium. The interpreter already does the translation. This is that call aimed at grant rules.
- **Sequence:** support for 3
- **Parents:** IDEA-009, IDEA-004
- **Origin:** AI brainstorm, November 24, 2026

### Problem

A team adopting grants still has a document that says "passwords must be 8 characters." Someone has to translate that into grant rules.

### Proposal

The interpreter reads the login section the team pastes and proposes lifetime, single use, origins, and "reusable password forbidden." The engine checks the proposal. A person saves it.

### End-user decision

A person on the team saves the grant policy. The model does not register an origin or enable the client.

### Visibility

The team sees its own document and draft. The end user's personal agent is not involved unless they choose to run the draft themselves.

### Model may see

The public login text the team submitted.

### Model must not see

User tables, password hashes, live grants, provider keys.

### Deterministic core

The saved grant policy is enforced by IDEA-009 with AI off.

### Integration

The service's Keynest setup, beside client registration.

### Distinctiveness

The draft becomes the same kind of policy document as a password policy, for a service that is leaving passwords behind.

### Dependencies

IDEA-004 and IDEA-009.

### Open questions

none

## IDEA-011 — Password-store retirement checklist

- **Status:** captured
- **Interest:** medium. A fixed checklist may be enough. Hold the model until it is not.
- **Sequence:** support for 3
- **Parents:** IDEA-009
- **Origin:** AI brainstorm, November 24, 2026

### Problem

A team ready to stop storing passwords cannot see every place the old credential still lives.

### Proposal

The team selects schema and route names, such as a `password_hash` column. The model lists the spots to remove and the grant calls to add. It never receives a hash, a row, or a dump.

### End-user decision

The team chooses which names to share and which changes to make. The checklist does not edit their repository.

### Visibility

The team sees the checklist. Keynest does not retain the names they submitted beyond the request they chose to make.

### Model may see

Identifiers the team explicitly selected.

### Model must not see

Hash values, rows, environment files, grants, provider keys.

### Deterministic core

IDEA-009 works without this list. A hand-written checklist remains valid.

### Integration

Developer setup for an opted-in service.

### Distinctiveness

The list is about removing a password store, tied to a grant policy, rather than a generic code review.

### Dependencies

IDEA-009

### Open questions

Whether this stays a model feature. A fixed checklist of known patterns may be enough and would keep the model out.

## IDEA-012 — Refusal shown on the service's screen

- **Status:** captured
- **Interest:** medium. Known refusal codes are a template. The model is only for extra prose.
- **Sequence:** support for 3
- **Parents:** IDEA-009
- **Origin:** AI brainstorm, November 24, 2026

### Problem

A failed login that says only "access denied" hides whether the origin was wrong, the approval expired, or the user denied it.

### Proposal

Keynest returns a stable code. The service renders it on its own screen. Known codes use a template. A model may rephrase extra prose the service supplies. The approve control stays disabled whenever the code is a refusal.

### End-user decision

The user acts on the explanation. They are not asked to approve from inside the refusal.

### Visibility

The user and the service see the code. The template names the origin Keynest actually checked.

### Model may see

The refusal code and prose the service already shows its users.

### Model must not see

The grant, the master password, vault data, provider keys.

### Deterministic core

The code and the template. The screen is useful with AI disabled.

### Integration

The service's login screen, through the same library that starts the grant.

### Distinctiveness

The explanation is tied to a bound check the service cannot silently widen. A generic error page does not name the mismatched origin.

### Dependencies

IDEA-009. Standing constraint 6 covers the approval sentence. This idea covers refusals only.

### Open questions

none

## IDEA-013 — Policy packs

- **Status:** captured
- **Interest:** medium. Sharing a policy file is real. It is not the daily stuck moment. Hold.
- **Sequence:**
- **Parents:** IDEA-008
- **Origin:** Product review, November 24, 2026

### Problem

Teams that share a password standard copy it by hand into each system.

### Proposal

A versioned policy document can be shared and reviewed like any other config file. Applying it is a human action with a diff. The model does not publish a pack.

### End-user decision

A person applies the pack to their account or service.

### Visibility

Whoever already has the document. Publishing a pack is an explicit act.

### Model may see

none

### Model must not see

Secrets inside a badly authored pack. The loader rejects fields outside the schema.

### Deterministic core

The existing policy schema and diff.

### Integration

Library and, later, the themeable component.

### Distinctiveness

The pack is the enforcement document, not a PDF standard beside a different generator.

### Dependencies

IDEA-008 if the pack is applied through the component. The library can load a document before that.

### Open questions

Who is allowed to publish a pack to someone else's account. Default in this record: nobody. The receiver applies it.

## IDEA-014 — CI policy check

- **Status:** captured
- **Interest:** medium. The same document can fail a build. Hold until someone asks to run it in CI.
- **Sequence:**
- **Parents:** none
- **Origin:** Product review, November 24, 2026

### Problem

A repository can propose a secret that would not pass the policy the team claims to use.

### Proposal

A build runs `keynest-policy` against candidates it already has and fails the check when they do not pass. The candidate is not logged.

### End-user decision

The team chooses to add the check and chooses the policy file.

### Visibility

The CI log shows pass or fail and the violation codes.

### Model may see

none

### Model must not see

The candidate secret.

### Deterministic core

`keynest-policy` in the pipeline.

### Integration

Library called from CI.

### Distinctiveness

The same document the vault enforces also fails a build.

### Dependencies

Published library coordinates make this easier. Source installation is enough to prove it.

### Open questions

How the candidate is supplied so the log and the model context cannot see it. The check must not print the value.

## IDEA-015 — Staged rotation

- **Status:** captured
- **Interest:** medium. The lockout fear is real. Hold for evidence and an ADR before a second secret is stored.
- **Sequence:**
- **Parents:** IDEA-001
- **Origin:** Product review, November 24, 2026

### Problem

Replacing a saved password before the website accepts the new one, or the reverse, leaves the user locked out or holding two truths.

### Proposal

Keep the current sealed secret and a pending replacement. The user commits the replacement after they have changed it on the site, or discards it.

### End-user decision

The user commits or discards. Nothing swaps on a timer.

### Visibility

The signed-in user, inside the bounded reveal. The pending secret is sealed like any other vault secret.

### Model may see

none

### Model must not see

Either password.

### Deterministic core

Vault seal and the existing rotate validation.

### Integration

Vault rotate flow.

### Distinctiveness

The vault can hold the outgoing and incoming secret without pretending it changed the website.

### Dependencies

Evidence from the rules-check and companion that users actually get stuck between the two copies. A new stored secret needs an ADR.

### Open questions

Whether the pending secret is a second envelope on the item or a short-lived row.

## IDEA-016 — Read-only agent status

- **Status:** captured
- **Interest:** low. The overview already shows the histogram. An agent view waits on its ADR.
- **Sequence:**
- **Parents:** IDEA-001
- **Origin:** Charter hypothesis and the product review, November 24, 2026

### Problem

An agent that helps with the vault is pushed toward seeing secrets. The useful question is narrower: how many logins fail, and which codes.

### Proposal

A read-only tool returns the rules-check summary for the authenticated owner. It cannot reveal, export, or approve.

### End-user decision

The user connects the agent and can disconnect it. The tool's schema does not grant authority.

### Visibility

The agent sees counts, codes, and policy ids. The user sees the same summary in the vault.

### Model may see

The summary the tool returns.

### Model must not see

Passwords, grants, provider keys, export files.

### Deterministic core

IDEA-001. The tool is a view over that result.

### Integration

A later agent adapter. Not part of the three design passes.

### Distinctiveness

The agent's answer is the engine's histogram. It is not a search over the vault.

### Dependencies

IDEA-001. The agent-gateway ADR in the roadmap. Out of sequence until the three design passes exist.

### Open questions

none beyond the ADR the roadmap already requires.

## IDEA-017 — Companion and embedded policy meet locally

- **Status:** captured
- **Interest:** high. The two products meet on the page without either side seeing the other's secrets.
- **Sequence:** support for 1 and 2
- **Parents:** IDEA-005, IDEA-008
- **Origin:** Follow-up brainstorm, November 24, 2026

### Problem

A person can face two standards at once: the marketplace's password rule on the page, and their own Keynest policy. Sending either side's draft to the other side leaks a policy the person did not agree to share.

### Proposal

When the companion and the embedded component are both on the page, they compare rules in the browser. The person sees the clash. The marketplace receives only pass or fail against the standard it defined. The person's vault, agent completion, and personal draft stay in the browser.

### End-user decision

The person chooses whether to connect the companion on that page. They choose whether to save a site exception. The marketplace does not get a control that edits the person's policy.

### Visibility

The person sees both policies. The site sees its own pass or fail result. Keynest as an operator sees neither comparison.

### Model may see

none for the comparison. A draft still follows IDEA-004 and only if the person asks.

### Model must not see

The marketplace user database, the candidate password, the person's other vault items.

### Deterministic core

Policy intersection in the browser, using the same schema as `keynest-policy`. No model is required.

### Integration

Browser companion beside the themeable component.

### Distinctiveness

The site learns the result of its own rule. It does not learn the person's stricter standard or their agent output.

### Dependencies

IDEA-005 and IDEA-008. The design of either one should leave a local comparison hook.

### Open questions

How the component discovers the companion without a network call to a Keynest service.

## IDEA-018 — Violation fixtures

- **Status:** captured
- **Interest:** high. The library can prove a policy with one pass and one fail per code. No model.
- **Sequence:** support for 2
- **Parents:** IDEA-008
- **Origin:** Follow-up brainstorm, November 24, 2026

### Problem

A team that embeds Keynest Policy still has to hand-write passwords that fail specific rules if they want to test the signup form.

### Proposal

For a saved policy, generate one synthetic password that passes and one that fails each violation code. The team uses the set in tests. The values are not stored in a vault.

### End-user decision

The developer chooses when to generate the set and where to put it. Generation does not create vault items.

### Visibility

Whoever runs the command sees the synthetic values. They are test material. Logs of a normal validation must still print codes, not the values.

### Model may see

none

### Model must not see

The fixtures, production passwords, or customer rows.

### Deterministic core

`keynest-generate` and `PasswordValidator`. No model.

### Integration

Library command or a button on the embedded component's developer view.

### Distinctiveness

The policy engine is also the test fixture. A password manager's generator does not emit one failure per rule code.

### Dependencies

IDEA-008 for the component button. The library can do this before the component exists.

### Open questions

How a CI log is kept from printing the fixture values when a test fails.

## IDEA-019 — Grant receipt and revoke

- **Status:** captured
- **Interest:** high. An approval the person cannot reread or revoke is an incomplete grant.
- **Sequence:** support for 3
- **Parents:** IDEA-009
- **Origin:** Follow-up brainstorm, November 24, 2026

### Problem

After approving a session, the person cannot see what they approved, and they cannot take it back if the service keeps its own session.

### Proposal

Keynest stores a sanitized receipt: app, origin, time, expiry, and whether the grant was used, denied, or expired. The grant secret is not stored in the receipt. If the service registered a revoke endpoint, the person can send a revoke from that receipt. The service is responsible for ending its session.

### End-user decision

The person approves, and the person revokes. Neither action is taken by a model.

### Visibility

The signed-in user sees their receipts. The service sees the revoke request for grants it issued. A messenger is not used.

### Model may see

none

### Model must not see

The grant secret, the master password, vault passwords.

### Deterministic core

The receipt is filled from the bound grant fields. Revoke is an authenticated call the user starts. Copy stays on the template from standing constraint 6.

### Integration

Keynest session history, and the service library's revoke handler.

### Distinctiveness

The receipt is proof of a single-use, origin-bound approval. It is not an OAuth token list and it does not contain a reusable password.

### Dependencies

IDEA-009. Revoke needs the service to register an endpoint. The receipt is useful even when revoke is absent.

### Open questions

How long receipts are kept, and whether a denied attempt is recorded. Default in this record: the user can clear receipts, and denied attempts are recorded without the attempted secret.

## IDEA-020 — Policy per credential kind

- **Status:** captured
- **Interest:** high. Favorite of the set. Kinds are enforced with no model. Design this schema beside the companion, not after the browser exists.
- **Sequence:**
- **Parents:** none
- **Origin:** Follow-up brainstorm, November 24, 2026. The maintainer called this out as a favorite.

### Problem

A login password, a backup passphrase, an API token, and a recovery code are different secrets. One composition form describes all of them badly. An API token has a vendor's format. A recovery code is shown once. A backup passphrase must not be the account password.

### Proposal

Each credential kind has its own policy document and the same generate-and-validate contract. Kinds start with login password, backup passphrase, API token, and recovery-code set. The engine enforces the document for that kind. A vendor's public format note can be turned into a draft through IDEA-004. That draft is optional. The kinds, the validation, and generation work with no model at all.

### End-user decision

The person chooses the kind when they save. They confirm any draft before it becomes the policy for that kind. They choose the backup passphrase. A local compare rejects a backup passphrase that matches the account password, without either value leaving the device.

### Visibility

The person sees the policy for each kind. A marketplace embedding IDEA-008 sees only the kind it asked to enforce, usually login passwords.

### Model may see

Public format text the person submits for an optional draft.

### Model must not see

Any secret of any kind, including the account password and the backup passphrase.

### Deterministic core

Schema and `PasswordValidator` per kind, plus `keynest-generate` when the kind is generated rather than chosen. No inference is on the path.

### Integration

Vault save and rotate, the library, and the themeable component for the login-password kind.

### Distinctiveness

Item types in other managers are labels and fields. Here the type is the enforcement document shared by generate and validate.

### Dependencies

A schema ADR when a kind needs fields the current password schema does not have. Recovery-code display follows IDEA-021.

### Open questions

Which kinds ship in the first schema. This record's starting set is the four named above.

## IDEA-021 — Factor separation

- **Status:** captured
- **Interest:** high. The default keeps the second factor in its own item. The person opts out per item.
- **Sequence:** support for 20
- **Parents:** IDEA-020
- **Origin:** Research note, November 24, 2026

### Problem

Password managers offer to store the TOTP seed, and often the recovery codes, inside the same item as the password. One unlocked vault then satisfies both factors. Recovery codes also end up in screenshots.

### Proposal

A TOTP seed and a recovery-code set are their own credential kinds. The default save refuses to put them inside the login item. The vault says what that choice means. Recovery codes are shown once, then sealed, and the item shows how many remain. The person can override the default for a named item.

### End-user decision

The person overrides the default per item. The default itself is separation. The product does not silently combine the factors.

### Visibility

The person sees the warning and the remaining-code count. The seed and the codes are sealed. They are not a field on the login item unless the person overrides that.

### Model may see

none

### Model must not see

The seed, the codes, or the login password.

### Deterministic core

Item kind and the default colocation rule. No model.

### Integration

Vault save, as a rule of IDEA-020.

### Distinctiveness

Other managers treat a built-in authenticator as a convenience feature. The default here is that the second factor is a different item, and the person has to choose otherwise.

### Dependencies

IDEA-020. This does not add a TOTP generator. Storage and the colocation rule come first.

### Open questions

Whether Keynest should generate TOTP codes at all. This record stops at storage and separation.

## IDEA-022 — Length-and-blocklist policy, calendar rotation off

- **Status:** captured
- **Interest:** high. The engine should be able to say length and a local blocklist, with calendar rotation off.
- **Sequence:**
- **Parents:** IDEA-020
- **Origin:** Research note, November 24, 2026. [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html).

### Problem

Composition rules and a 90-day change calendar are what many sites still demand, and they are what current NIST guidance tells a verifier not to do. People get both messages and cannot tell which policy they are satisfying.

### Proposal

Ship a policy preset that requires length, accepts a long maximum, imposes no character-class mix, and checks a blocklist the operator or the person supplies locally. Periodic rotation is off unless that person sets an expiry. The rules check and a real compromise signal are the reasons to rotate. Websites that still publish composition rules stay on the companion path in IDEA-005. The blocklist is a file or a policy field the person loaded. Keynest does not send the candidate to a breach-search service.

### End-user decision

The person selects this preset or a composition policy. They choose whether a given kind has an expiry date. They choose the blocklist file.

### Visibility

The person sees which preset is active. The blocklist file stays where they put it.

### Model may see

none

### Model must not see

The candidate password or the blocklist entries.

### Deterministic core

`keynest-policy` with composition flags off, a minimum length, and the existing denylist check against a local list.

### Integration

Account policy, and the library for a service that wants this preset instead of composition rules.

### Distinctiveness

The same engine can express the site's composition rule and the length-and-blocklist preset. The calendar is not the product.

### Dependencies

IDEA-020 if expiry belongs to the kind. The preset can exist for login passwords before other kinds do.

### Open questions

How large a local blocklist is honest to ship. This record does not include a phone-home breach API.

## IDEA-023 — Local paste guard

- **Status:** captured
- **Interest:** medium. The paste path is real. Hold until the false positives of a short prefix list are understood.
- **Sequence:**
- **Parents:** none
- **Origin:** Research note, November 24, 2026. Clipboard paste into chats, and the August 2025 AgentFlayer demonstration of API keys leaving a connected drive through a chat tool.

### Problem

A person copies a token or a password and pastes it into a chat box. The secret is then in the provider's request. File-scanning tools do not see that paste.

### Proposal

On the person's machine, before they send text to a model, a local check flags credential-shaped strings: known token prefixes and high-entropy tokens. The person sees the flag and chooses to redact, send, or cancel. The check does not upload the text to Keynest. It does not open the vault to compare values.

### End-user decision

The person decides whether the flagged text is sent. The guard does not delete the clipboard on its own.

### Visibility

The person sees the flag on their machine. The model sees the text only if they still choose to send it.

### Model may see

none during the check.

### Model must not see

The pasted secret, unless the person overrides the flag and sends it anyway. The guard's job is to make that override explicit.

### Deterministic core

Local pattern checks. No model and no vault decrypt.

### Integration

Browser companion, or a small local tool beside the BYOK client. It is not a Keynest server endpoint.

### Distinctiveness

The check runs before the prompt is sent, on the client, and it does not need the vault's secrets to notice a token shape.

### Dependencies

none. It is more useful once API tokens exist as a kind in IDEA-020, because the warning can name the kind.

### Open questions

Which prefixes are worth flagging without a flood of false matches. The first set should be short and documented.

## IDEA-024 — Service asks for a phishing-resistant authenticator

- **Status:** captured
- **Interest:** medium. A flag that refuses a password grant. Not a passkey wallet.
- **Sequence:** support for 3
- **Parents:** IDEA-009
- **Origin:** Research note, November 24, 2026. [OWASP passkey cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Passkey_Security_Cheat_Sheet.html).

### Problem

A reusable password is phishable. Passkeys fix that for sites that implement them. Building another passkey wallet would copy Apple, Google, and the existing password managers, and it would not fix sharing, revocation, or recovery by itself.

### Proposal

An opted-in service can set a policy flag: reusable password forbidden, phishing-resistant authenticator required. Keynest records that as the service's rule and will not issue a password-shaped grant for it. Keynest does not store or sync passkeys in this idea. Recovery for that service uses a recovery-code kind from IDEA-020, shown once and kept separate from the login. Revocation uses IDEA-019.

### End-user decision

The service operator sets the flag. The person decides whether to register that service and whether to store the recovery codes in Keynest.

### Visibility

The person sees the flag on the service's registration screen. No passkey private key is collected.

### Model may see

none

### Model must not see

Passwords, recovery codes, or authenticator secrets.

### Deterministic core

The grant policy refuses a reusable-password grant when the flag is set. No model.

### Integration

Service registration in IDEA-009.

### Distinctiveness

The policy document can say "this service has left passwords." The vault does not pretend to be the passkey provider.

### Dependencies

IDEA-009. IDEA-019 and IDEA-020 cover the recovery and revoke gaps that remain after passkeys.

### Open questions

none for this limited flag. A passkey provider is a different product and is not proposed here.

---

## Rejected

### IDEA-R01 — Session password delivered to the user

- **Status:** rejected
- **Origin:** Maintainer proposal, November 24, 2026, refined the same day
- **Proposal:** A site asks Keynest for a password, Keynest sends it through the user's chosen channel, and the user types it into the site for that session only.
- **Why rejected:** The channel, the site, and Keynest would all see a secret the user must retype. Any page can ask for that paste. Threat T17 already forbids putting plaintext in a notification. Once a site can ask Keynest, it can receive a grant without the user carrying a secret.
- **Superseded by:** IDEA-009

### IDEA-R02 — Model approves or relaxes a rule

- **Status:** rejected
- **Origin:** AI brainstorm, November 24, 2026
- **Proposal:** The model approves a grant, or lowers a policy when a site is stricter or weaker.
- **Why rejected:** Standing constraints 1 and 3. The end user decides. The engine decides validity.
- **Superseded by:** IDEA-005 for conflicts, IDEA-009 for approval

### IDEA-R03 — Model reads or fills the password field

- **Status:** rejected
- **Origin:** AI brainstorm, November 24, 2026
- **Proposal:** The companion sends the password field to the model, or the model types the password.
- **Why rejected:** Standing constraint 5. Generation can fill from the generator's own output later, without the model, and only if a later design asks for fill.
- **Superseded by:** IDEA-005

### IDEA-R04 — Chat on the vault or the login page

- **Status:** rejected
- **Origin:** AI brainstorm, November 24, 2026
- **Proposal:** A general assistant beside the vault or embedded on a login form.
- **Why rejected:** It does not resolve a rule conflict or a grant refusal, and it invites extra context into the prompt.
- **Superseded by:** IDEA-004

### IDEA-R05 — Server fetches any URL the user pastes

- **Status:** rejected
- **Origin:** AI brainstorm, November 24, 2026
- **Proposal:** Keynest's server downloads a page or a document URL to read the password rules.
- **Why rejected:** Standing constraint 7. An open fetch is an open proxy.
- **Superseded by:** IDEA-005. The user confirms sentences from a page they already opened.

### IDEA-R06 — Narration as the whole AI product

- **Status:** rejected
- **Origin:** AI brainstorm, November 24, 2026
- **Proposal:** The only model feature is rewriting violation codes and refusal codes as sentences.
- **Why rejected:** A template can say "origin mismatch" or "too short." The model earns its place by reading prose and producing a policy draft.
- **Superseded by:** IDEA-004. IDEA-003 and IDEA-012 remain optional narration around that.

### IDEA-R07 — Keynest logs into sites that have not opted in

- **Status:** rejected
- **Origin:** Login-grant discussion, November 24, 2026
- **Proposal:** LinkedIn, or any consumer site, delegates its login to a person's self-hosted Keynest without that site registering Keynest.
- **Why rejected:** The site has to opt in. Sites that will not opt in stay stored-password sites and are served by the browser companion.
- **Superseded by:** IDEA-005 for those sites, IDEA-009 for sites that register
