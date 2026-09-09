# Board reference

Board mechanics follow **TokenForge / Flux** (GitHub Project Status columns + PR/epic lifecycle Actions).
Issue writing follows **Lume**: every story has **Description**, **Approach**, and **Definition of done** — not a Goal/AC-only card.

Companion: [PROJECT_PR_WORKFLOW.md](../PROJECT_PR_WORKFLOW.md), [ROADMAP.md](../ROADMAP.md), [PRODUCT.md](../PRODUCT.md).

Project board: **[Keynest — MVP Board](https://github.com/users/JordyNicholas/projects/4)** (created and seeded via `scripts/setup-github-project.sh`). Re-run that script with `--link-issues` to add newly created issues and set their Status. The `PROJECT_*` / `STATUS_*` IDs are wired into the lifecycle workflows under [`.github/workflows/`](../../.github/workflows/).

## Labels

| Prefix | Purpose |
| --- | --- |
| `epic` | Phase container |
| `phase:e0` … `phase:e6`, `phase:future` | Roadmap swimlane |
| `priority:p0` / `p1` / `p2` | Must / should / later |
| `type:seal` `type:policy` `type:generate` `type:api` `type:web` `type:platform` `type:notify` `type:security` `type:docs` | Owner surface |
| `estimate:*` | Rough size |
| `good first issue` | Safe first PR |

Filter examples: `is:open label:phase:e1` or `label:priority:p0`.

## Issue anatomy (Lume)

| Section | Required | Content |
| --- | --- | --- |
| Description | Yes | Short problem statement. Who is blocked. Not a novel. |
| Approach | Yes | Ordered suggestion of *what* to do and *where*. Pointers to packages/docs. Risks. Not a line-by-line script. |
| Definition of done | Yes | Testable checkboxes. Include tests/docs when they apply. |
| Parent | Stories | Epic issue number + native GitHub sub-issue link |
| Estimate | Stories | Label + one line |

Epics list a roll-up DoD (“all p0 children closed”).

## Board Status (TokenForge / Flux)

Columns: **Todo → In Progress → Ready for Review → Done** (+ **Epics Finished** for epics).

| Event | Board Status |
| --- | --- |
| Draft PR opened / converted to draft | In Progress |
| PR opened ready for review / marked ready | Ready for Review |
| Commits pushed to a non-draft PR | Ready for Review |
| PR merged / linked **story** issue closed | Done |
| Last child story of an **`[Epic]`** closed | Epic auto-closes → **Epics Finished** |
| **`[Epic]` issue closed** | **Epics Finished** |
| Child story of a closed **`[Epic]`** reopened | Epic reopens → Todo |
| PR closed without merge | Todo |

## Branch naming

```text
KN#<issue-number>
KN#<issue-number>-short-slug
KEYNEST#<issue-number>
```

## Phases

| Phase | Epic | Intent |
| --- | --- | --- |
| E0 | #6 Foundation gates | CI, threat model stub, demo-key gate, rate limits, API base URL |
| E1 | #7 Seal & policy harden | Golden vectors, `rewrap`, `explain`, buffer hygiene |
| E2 | #8 Material shell | Angular Material theme, router shell, tools page migrate |
| E3 | #9 Vault auth API | Auth + vault envelopes + Postgres Compose |
| E4 | #10 Vault UI | Auth/vault screens on Material shell, rotate, tests |
| E5 | #11 Expiry notify | Outbox, email adapter, upcoming expiry UI |
| E6 | #12 Ship & embeddable proof | Compose runbook, external consumer, release hygiene |
| Future | #13 Post-MVP | MFA, recovery key, sidecar, ZK client open |

## Story index

### E0 — Foundation (#6)

- #14 CI: Maven test + Angular build on PRs *(closed — shipped)*
- #15 Threat model stub *(closed — shipped)*
- #16 Gate tools seal/open demo key for production
- #17 Rate-limit tools validate and auth endpoints
- #18 Angular `API_BASE_URL` via environments

### E1 — Seal & policy (#7)

- #19 Seal v1 golden vectors *(closed — shipped)*
- #20 Implement `rewrap`
- #21 Policy `explain()` + richer denylist
- #22 Sensitive buffer hygiene

### E2 — Material shell (#8)

- #23 Angular Material + Keynest-themed tokens
- #24 App shell routes: Tools \| Vault \| Account
- #25 Migrate tools demo to routed Material page
- #26 Shared UI primitives

### E3 — Vault auth API (#9)

- #27 Auth register/login *(closed — shipped)*
- #28 Vault CRUD envelopes *(closed — shipped)*
- #29 Compose Postgres *(closed — shipped)*
- #30 Server DEK/KEK from env — no demo hex in prod

### E4 — Vault UI (#10)

- #31 Auth screens on Material shell
- #32 Vault list + create / reveal / update / delete
- #33 Generate-on-create and validate-before-save
- #34 Expiry fields + guided rotate
- #35 Critical vault flow tests

### E5 — Expiry notify (#11)

- #36 Notify outbox + scheduler
- #37 Email adapter (Mailhog/console + cloud)
- #38 UI: upcoming expiry + rotate deep link
- #39 Outbox state machine tests

### E6 — Ship (#12)

- #40 Full Compose polish + runbook
- #41 Sample external consumer module
- #42 README success criteria + publish notes
- #43 Threat model release pass + logging review
- #44 GitHub repository rename checklist (operator)

### Future (#13)

- #45 MFA (TOTP)
- #46 Downloadable recovery key
- #47 Tools HTTP sidecar
- #48 Zero-knowledge client-side open
