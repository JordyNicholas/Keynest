# Keynest idea catalog

**Status:** proposed. Brainstorming is open. Interest is unrated on purpose.
**Captured:** November 24, 2026 (America/Sao_Paulo)
**Format:** [FORMAT.md](FORMAT.md)

This catalog collects the ideas from the November 24 product conversation. It is not a release commitment. The [canonical roadmap](../../MASTER_ROADMAP.md) and accepted ADRs still govern what is in scope. Nothing here is shipped.

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

## Index

| Id | Title | Status | Sequence | Interest |
| --- | --- | --- | --- | --- |
| IDEA-001 | Rules check | captured | support for 1 | unrated |
| IDEA-002 | Policy impact preview | captured | support for 1 | unrated |
| IDEA-003 | Queue narrator | captured | support for 1 | unrated |
| IDEA-004 | Text-to-policy interpreter | captured | support for 1 | unrated |
| IDEA-005 | Browser companion | captured | 1 | unrated |
| IDEA-006 | Per-site policy | captured | support for 1 | unrated |
| IDEA-007 | Policy drift report | captured | | unrated |
| IDEA-008 | Distributed policy tool and themeable UI | captured | 2 | unrated |
| IDEA-009 | Single-use access workflows | captured | 3 | unrated |
| IDEA-010 | Grant-policy draft | captured | support for 3 | unrated |
| IDEA-011 | Password-store retirement checklist | captured | support for 3 | unrated |
| IDEA-012 | Refusal shown on the service's screen | captured | support for 3 | unrated |
| IDEA-013 | Policy packs | captured | | unrated |
| IDEA-014 | CI policy check | captured | | unrated |
| IDEA-015 | Staged rotation | captured | | unrated |
| IDEA-016 | Read-only agent status | captured | | unrated |
| IDEA-R01 | Session password delivered to the user | rejected | | |
| IDEA-R02 | Model approves or relaxes a rule | rejected | | |
| IDEA-R03 | Model reads or fills the password field | rejected | | |
| IDEA-R04 | Chat on the vault or the login page | rejected | | |
| IDEA-R05 | Server fetches any URL the user pastes | rejected | | |
| IDEA-R06 | Narration as the whole AI product | rejected | | |
| IDEA-R07 | Keynest logs into sites that have not opted in | rejected | | |

## Evaluation

Brainstorming is **open**. Do not fill Interest yet.

When it is closed, score every open idea and place it in one column:

| Design now | Keep as support | Hold | Reject |
| --- | --- | --- | --- |
| Reserved for the highest-interest ideas that fit the standing constraints. | Ideas a chosen design needs, without being the thing a person installs. | Sound ideas that wait for evidence or an ADR. | Break a standing constraint, or duplicate a rejected record. |

Suggested test, applied in this order:

1. The end user has a concrete stuck moment, and they remain the one who decides.
2. The deterministic core still helps them with AI disabled.
3. Comparable password managers or identity products do not already do this.
4. The integration is a vault screen, a browser companion, a library, a themeable component, or an opted-in app.
5. No new cipher, and no secret in the model.

## How to add an idea

Copy the field list from [FORMAT.md](FORMAT.md). Use the next free `IDEA-NNN`. Link parents. Leave Interest as `unrated`. Add a row to the index. If the idea breaks a standing constraint, put it in Open questions or mark it rejected.

---

## IDEA-001 — Rules check

- **Status:** captured
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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

- **Status:** captured
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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

- **Status:** captured
- **Interest:** unrated
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

- **Status:** captured
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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
- **Interest:** unrated
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
