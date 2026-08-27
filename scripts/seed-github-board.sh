#!/usr/bin/env bash
# Seed Keynest GitHub labels + epics + stories.
# Board mechanics: TokenForge / Flux (phases, priorities, estimates, Project workflows).
# Issue anatomy: Lume (Description / Approach / Definition of done).
#
# Usage: ./scripts/seed-github-board.sh JordyNicholas/Keynest
set -euo pipefail

REPO="${1:-JordyNicholas/Keynest}"

label() {
  local name="$1" color="$2" desc="$3"
  gh label create "$name" --color "$color" --description "$desc" -R "$REPO" 2>/dev/null \
    || gh label edit "$name" --color "$color" --description "$desc" -R "$REPO"
}

echo "Labels on $REPO"
label "epic" "6f42c1" "Epic / scope container"
label "type:seal" "1d76db" "keynest-seal crypto lib"
label "type:policy" "0052cc" "keynest-policy standards"
label "type:generate" "0e8a16" "keynest-generate"
label "type:api" "5319e7" "Manager API (Spring)"
label "type:web" "d93f0b" "Manager Web (Angular)"
label "type:platform" "6f42c1" "CI / Compose / env / packing"
label "type:notify" "fbca04" "Expiry notify / outbox"
label "type:security" "b60205" "Threat model / auth / keys"
label "type:docs" "0075ca" "Documentation"
label "priority:p0" "b60205" "Must for MVP path"
label "priority:p1" "d93f0b" "Should for MVP"
label "priority:p2" "fbca04" "Nice / later"
label "phase:e0" "5319e7" "E0 Foundation"
label "phase:e1" "1d76db" "E1 Seal & policy harden"
label "phase:e2" "0052cc" "E2 Material shell"
label "phase:e3" "006b75" "E3 Vault auth API"
label "phase:e4" "b60205" "E4 Vault UI"
label "phase:e5" "0e8a16" "E5 Expiry notify"
label "phase:e6" "6f42c1" "E6 Ship & embeddable proof"
label "phase:future" "e99695" "Future / not MVP"
label "estimate:0.5d" "c2e0c6" "Half day"
label "estimate:1d" "c2e0c6" "1 workday"
label "estimate:2d" "bfdadc" "2 workdays"
label "estimate:3d" "fef2c0" "3 workdays"
label "estimate:5d" "f9d0c4" "5 workdays"

create() {
  gh issue create -R "$REPO" --title "$1" --label "$2" --body "$3"
}

echo "Epics"
E0=$(create "[Epic] E0 — Foundation gates" "epic,phase:e0,priority:p0" "$(cat <<'EOF'
## Description

Later crypto, vault, and UI work is unsafe to land without CI, a written threat model, and production-safe tools/auth defaults. CI already runs; the security baseline gaps remain.

## Approach

1. Keep CI green on every PR (`mvn test` + Angular build).
2. Land `docs/THREAT_MODEL.md` stub before public release expansion.
3. Gate or remove the hardcoded demo DEK on tools seal/open; inject Angular `API_BASE_URL`.
4. Rate-limit tools validate and auth endpoints.

Cite: `docs/ROADMAP.md` Phase 0, `docs/PRODUCT.md` security non-negotiables.

## Definition of done

- [ ] All `priority:p0` children of this epic closed
- [ ] CI green on `main`
- [ ] No production profile ships a fixed hex DEK for tools/vault demo paths
- [ ] `docs/reference/board.md` lists this epic accurately
EOF
)" | awk -F/ '{print $NF}')

E1=$(create "[Epic] E1 — Seal and policy hardening" "epic,phase:e1,priority:p0" "$(cat <<'EOF'
## Description

Manager must not treat envelopes as durable product data until Seal v1 is locked. Golden vectors already land; `rewrap`, policy `explain`, and buffer hygiene still need to finish the crypto contract.

## Approach

1. Keep golden vectors failing the build on envelope drift.
2. Implement `rewrap` + round-trip tests; amend ADR-0003 if AAD/layout fields change.
3. Add `explain(policy)` and richer denylist coverage without breaking generate↔validate.
4. Clear sensitive intermediates after seal/open/hash where practical.

Do not start E3 vault persistence work that assumes a stable envelope until p0 children here are done (golden vectors already unblock storage that already shipped — treat remaining E1 as harden-before-ship).

## Definition of done

- [ ] All `priority:p0` children closed
- [ ] Golden vectors + `rewrap` round-trips pass in CI
- [ ] PRODUCT “Harden libs” spirit satisfied
EOF
)" | awk -F/ '{print $NF}')

E2=$(create "[Epic] E2 — Angular Material shell (Keynest-themed)" "epic,phase:e2,priority:p0" "$(cat <<'EOF'
## Description

Vault screens must not be built twice. Manager needs Angular Material primitives themed to the Keynest forest palette, with a routed shell, before polishing vault UX.

## Approach

1. Add `@angular/material` + CDK matching the Angular major in the repo.
2. Map Material theme tokens to existing Keynest colors (reject default indigo).
3. Shell: brand mark + nav placeholders Tools | Vault | Account; router outlets.
4. Migrate the tools demo off the monolithic `AppComponent` into a routed Material page.

Marketing/landing surfaces stay custom CSS — Material is for authenticated interaction chrome only.

## Definition of done

- [ ] All `priority:p0` children closed
- [ ] Tools demo is a routed page, not the entire `AppComponent` blob
- [ ] Visual identity still reads as Keynest (no Material indigo defaults)
- [ ] No new vault CRUD feature work that bypasses this shell
EOF
)" | awk -F/ '{print $NF}')

E3=$(create "[Epic] E3 — Manager API: auth + vault + Postgres" "epic,phase:e3,priority:p0" "$(cat <<'EOF'
## Description

First-party Manager must persist sealed credentials behind authenticated users. Auth, vault CRUD, Compose Postgres, and integration tests largely landed; remaining gaps are production key handling and auth rate limits shared with E0.

## Approach

Treat shipped CRUD as the baseline. Close remaining children only for real gaps (key config, recovery stub copy, unauthorized paths). Controllers stay thin: validate → use case → libs → DTO. Libs never take Spring types.

## Definition of done

- [ ] All `priority:p0` children closed or explicitly deferred to E0/E6 with a link
- [ ] Authenticated CRUD stores only Seal envelopes at rest
- [ ] External integrators still depend only on public lib APIs
EOF
)" | awk -F/ '{print $NF}')

E4=$(create "[Epic] E4 — Manager Web vault CRUD" "epic,phase:e4,priority:p0" "$(cat <<'EOF'
## Description

Users need a usable vault on the Material shell: auth, list/create/reveal/update/delete, generate-on-create, and expiry/rotate affordances. A first create/reveal/delete path exists in the monolithic component; it must move onto the E2 shell and finish the product loop.

## Approach

1. Finish E2 shell before rewriting vault screens.
2. Auth + vault routes with Material inputs; brand-first layout without packing secondary chrome into the first viewport.
3. Reveal via authenticated server-side `open`; generate/validate through tools or vault use cases.
4. Expiry fields + guided rotate (notify lands in E5).
5. Critical flow tests (auth guard + list load) at minimum.

## Definition of done

- [ ] All `priority:p0` children closed
- [ ] End-to-end: register → create → reveal → update → delete against Compose API + DB
- [ ] Vault UI lives on the Material shell, not a single-file demo
EOF
)" | awk -F/ '{print $NF}')

E5=$(create "[Epic] E5 — Expiry notify" "epic,phase:e5,priority:p0" "$(cat <<'EOF'
## Description

Keynest Manager’s differentiator is warning before password age/expiry. Without an outbox + email path, expiry fields are display-only.

## Approach

1. Scheduler/outbox scanning `expiresAt` / `notifyBeforeDays` / policy `maxAgeDays`.
2. Email adapter: Mailhog/console locally; provider config for cloud.
3. UI: upcoming expiry list + “rotate now” into the E4 rotate flow.
4. Tests for outbox pending → sent / failed.

## Definition of done

- [ ] All `priority:p0` children closed
- [ ] Local Compose can demonstrate a due notification without a real ESP
EOF
)" | awk -F/ '{print $NF}')

E6=$(create "[Epic] E6 — Ship path and embeddable proof" "epic,phase:e6,priority:p0" "$(cat <<'EOF'
## Description

The platform story is incomplete until Compose is documented end-to-end, a sample external consumer uses only the libs, and release hygiene (threat model, logging, rename) is done.

## Approach

1. Polish Compose (Postgres + API + web notes / Vercel).
2. Add `examples/external-consumer` depending on seal/policy/generate only.
3. Update README success criteria; GitHub Packages notes (Central later).
4. Expand threat model; review rate limits and secret logging.
5. Manual GitHub rename checklist item for the operator.

## Definition of done

- [ ] All `priority:p0` children closed
- [ ] PRODUCT success criteria satisfied
- [ ] Sample consumer seals/validates/generates without Manager
EOF
)" | awk -F/ '{print $NF}')

FUT=$(create "[Epic] Future — post-MVP capabilities" "epic,phase:future,priority:p2" "$(cat <<'EOF'
## Description

Work we will not pitch as MVP: MFA beyond the stub, downloadable recovery keys, ZK client-side open, tools HTTP sidecar, Maven Central. Keeping it on the board prevents silent scope creep.

## Approach

Do not start these while E0–E6 p0 is open unless product grooming explicitly pulls one forward. Each child that changes the threat model gets an ADR.

## Definition of done

- [ ] Children stay labeled `phase:future`
- [ ] None of these ship as silent extras inside an E0–E6 PR
EOF
)" | awk -F/ '{print $NF}')

echo "Epics: E0=$E0 E1=$E1 E2=$E2 E3=$E3 E4=$E4 E5=$E5 E6=$E6 F=$FUT"

story() {
  local title="$1" labels="$2" parent="$3" body="$4"
  create "$title" "$labels" "$(printf '## Parent\n#%s\n\n%s' "$parent" "$body")"
}

echo "E0 stories"
story "[E0] CI: Maven test + Angular build on PRs" "type:platform,priority:p0,phase:e0,estimate:0.5d" "$E0" "$(cat <<'EOF'
## Description

Collaborators need a green gate on every pull request so lib and Manager regressions cannot land silently.

## Approach

1. Keep `.github/workflows/ci.yml` running `mvn -B test` (libs + manager-api) and `npm ci && npm run build` in `apps/manager-web`.
2. Do not add non-deterministic checks.

## Definition of done

- [x] Workflow present and runs on PRs / `main`
- [x] JDK matrix covers the toolchain policy in ADR-0005
- [ ] Branch protection requires the CI job when an admin enables it (operator)

## Estimate
0.5 day (mostly shipped)
EOF
)"

story "[E0] Threat model stub" "type:security,type:docs,priority:p0,phase:e0,estimate:0.5d" "$E0" "$(cat <<'EOF'
## Description

Seal/open, master passwords, and auth are in scope for abuse, but there is no `docs/THREAT_MODEL.md` yet. Release work will invent one under pressure without a stub.

## Approach

1. Add `docs/THREAT_MODEL.md` listing assets (master password, envelopes, DEKs), trust boundaries (browser / API / DB), and abuse cases (brute force, demo-key leakage, log scraping).
2. Keep it short; expand under E6 before public release.

## Definition of done

- [ ] File exists and lists seal/open and auth as in-scope
- [ ] Linked from ROADMAP / PRODUCT security section
- [ ] No novel cipher claims

## Estimate
0.5 day
EOF
)"

story "[E0] Gate tools seal/open demo key for production" "type:api,type:security,priority:p0,phase:e0,estimate:1d" "$E0" "$(cat <<'EOF'
## Description

`ToolsController` still carries a hardcoded demo DEK for seal/open demos. A production profile must not ship a fixed hex key.

## Approach

1. Gate seal/open behind config that is off by default in production, or require auth once vault auth is trusted.
2. Prefer vault use cases for real envelopes; keep tools generate/validate available.
3. Align with `VaultKeyProperties` / env-based key config.

## Definition of done

- [ ] No production profile ships a fixed demo hex DEK
- [ ] Local/dev demo path documented if retained
- [ ] Tests cover the gate

## Estimate
1 workday
EOF
)"

story "[E0] Rate-limit tools validate and auth endpoints" "type:api,type:security,priority:p0,phase:e0,estimate:1d" "$E0" "$(cat <<'EOF'
## Description

Validate and auth endpoints are brute-force surfaces. PRODUCT non-negotiables require rate limits before public exposure.

## Approach

1. Add a filter or bucket4j-style limiter on `/api/v1/tools/validate` and auth register/login.
2. Sensible defaults for local Compose; document overrides.

## Definition of done

- [ ] Repeated validate/auth attempts are limited
- [ ] Limits documented for self-host
- [ ] Happy-path tests still pass

## Estimate
1 workday
EOF
)"

story "[E0] Angular API_BASE_URL via environments" "type:web,type:platform,priority:p0,phase:e0,estimate:0.5d" "$E0" "$(cat <<'EOF'
## Description

Manager Web hardcodes `http://localhost:8080/api/v1` in `app.component.ts`, which breaks Vercel/cloud and Compose hostname setups.

## Approach

1. Introduce Angular environments or inject `API_BASE_URL`.
2. Document local vs Compose vs Vercel values in `docs/VERCEL.md` / README.

## Definition of done

- [ ] No hardcoded localhost API base in production builds
- [ ] Local default still works for `ng serve`
- [ ] Docs updated

## Estimate
0.5 day
EOF
)"

echo "E1 stories"
story "[E1] Seal v1 golden vectors" "type:seal,priority:p0,phase:e1,estimate:1d" "$E1" "$(cat <<'EOF'
## Description

Envelope layout must fail CI if it drifts. Golden vectors for seal/open/fingerprint already exist and should stay the contract lock.

## Approach

Keep `SealGoldenVectorTest` deterministic (fixed key / nonce fixture). Do not weaken vectors to make unrelated refactors pass.

## Definition of done

- [x] Deterministic golden vectors in `libs/keynest-seal`
- [x] Vectors cover seal / open / fingerprint behaviour
- [ ] Vectors still fail the build if envelope layout drifts (regression watch)

## Estimate
1 workday (shipped)
EOF
)"

story "[E1] Implement rewrap(envelope, oldKey, newKey)" "type:seal,priority:p0,phase:e1,estimate:2d" "$E1" "$(cat <<'EOF'
## Description

Key rotation without changing plaintext is part of the Seal toolkit contract and is required before treating envelopes as long-lived vault data under rotating KEKs.

## Approach

1. Implement `rewrap` on `KeynestSeal` with AAD/`policyId` binding preserved.
2. Round-trip tests; amend ADR-0003 appendix if layout fields change.
3. Do not invent a new cipher.

## Definition of done

- [ ] `rewrap` API + tests green in CI
- [ ] AAD binding documented
- [ ] Golden vectors still pass

## Estimate
2 workdays
EOF
)"

story "[E1] Policy explain() + richer denylist coverage" "type:policy,priority:p0,phase:e1,estimate:1d" "$E1" "$(cat <<'EOF'
## Description

UI and integrators need human-readable rule copy (`explain`) and stronger banned-list / denylist behaviour without breaking the generate↔validate invariant.

## Approach

1. Add `explain(policy)` returning structured or readable rule text for UI.
2. Expand banned-list / regex denylist coverage with tests.
3. Keep `keynest-generate` always satisfying `validate` for the same policy id.

## Definition of done

- [ ] `explain` covered by unit tests
- [ ] Denylist cases documented against `password-policy.schema.json`
- [ ] Generate↔validate invariant tests still pass

## Estimate
1 workday
EOF
)"

story "[E1] Sensitive buffer hygiene after seal/open/hash" "type:seal,type:security,priority:p1,phase:e1,estimate:1d" "$E1" "$(cat <<'EOF'
## Description

PRODUCT asks to clear sensitive `byte[]`/`char[]` where practical after seal/open/hash so heap dumps are less hospitable.

## Approach

1. Audit `keynest-seal` / hasher paths for intermediates that can be zeroed.
2. Prefer clear documentation where the JVM makes guarantees weak.
3. Do not break public APIs for hygiene theatre.

## Definition of done

- [ ] Intermediate buffers cleared where practical
- [ ] Short note in ADR-0001 or Seal ADR
- [ ] Existing tests still green

## Estimate
1 workday
EOF
)"

echo "E2 stories"
story "[E2] Add Angular Material + Keynest-themed tokens" "type:web,priority:p0,phase:e2,estimate:2d" "$E2" "$(cat <<'EOF'
## Description

Manager interaction primitives need Material, but default indigo chrome would erase the Keynest brand (Fraunces / forest greens).

## Approach

1. Add `@angular/material` + CDK matching the Angular major in `apps/manager-web`.
2. Custom theme (Sass or CSS variables) mapped to existing tokens in `styles.css`.
3. Preserve dark forest color-scheme.

## Definition of done

- [ ] Material dependency installed and building
- [ ] Theme tokens map to Keynest palette (no indigo defaults)
- [ ] Landing/brand surfaces stay custom CSS

## Estimate
2 workdays
EOF
)"

story "[E2] App shell routes: Tools | Vault | Account" "type:web,priority:p0,phase:e2,estimate:1d" "$E2" "$(cat <<'EOF'
## Description

`app.routes.ts` is empty and the demo lives in `AppComponent`. Vault work will thrash without a shell and router outlets.

## Approach

1. Layout with brand mark and primary nav placeholders: Tools | Vault | Account.
2. Router outlets; auth guard stubs as needed for later E4.
3. Keep the first authenticated viewport uncluttered (no card-hero demos).

## Definition of done

- [ ] Routes exist for Tools / Vault / Account placeholders
- [ ] Brand mark is visible in the shell
- [ ] `AppComponent` is no longer the entire product UI

## Estimate
1 workday
EOF
)"

story "[E2] Migrate tools demo to routed Material page" "type:web,priority:p0,phase:e2,estimate:1d" "$E2" "$(cat <<'EOF'
## Description

Health / generate / validate still sit in the monolithic component. They should become a routed tools page using Material form fields, buttons, and snackbars.

## Approach

1. Move tools demo into a routed component under the shell.
2. Material form fields / buttons / snackbars; keep non-card brand treatment on the tools intro.
3. Wire `API_BASE_URL` from E0 if already landed; otherwise leave a clear TODO seam.

## Definition of done

- [ ] Tools demo is a routed page
- [ ] Material controls used for forms/actions
- [ ] Brand treatment still reads as Keynest

## Estimate
1 workday
EOF
)"

story "[E2] Shared UI primitives: confirm, errors, password reveal" "type:web,priority:p1,phase:e2,estimate:1d" "$E2" "$(cat <<'EOF'
## Description

Vault flows need a small set of shared primitives so E4 does not invent one-off dialogs and password fields.

## Approach

Implement only what is needed: confirm dialog, inline error, password field with reveal. Reuse Material where it fits the theme.

## Definition of done

- [ ] Confirm dialog reusable from Vault delete/rotate
- [ ] Password field with reveal
- [ ] Inline error pattern documented by usage in Tools or Vault

## Estimate
1 workday
EOF
)"

echo "E3 stories"
story "[E3] Auth register/login with Argon2id + sessions" "type:api,type:security,priority:p0,phase:e3,estimate:2d" "$E3" "$(cat <<'EOF'
## Description

Manager users must authenticate with `PasswordHasher` (Argon2id) and a session/JWT model before touching vault items. This path already exists and should stay the reference.

## Approach

Keep thin controllers; explicit “reset wipes vault” recovery stub copy when account recovery is exposed. Rate limits tracked under E0.

## Definition of done

- [x] Register/login using Argon2id hasher
- [x] Session or token issued to the client
- [x] Integration tests for happy path + unauthorized
- [ ] “Reset wipes vault” UX copy stub documented when recovery is exposed

## Estimate
2 workdays (mostly shipped)
EOF
)"

story "[E3] Vault CRUD API storing Seal envelopes only" "type:api,type:seal,priority:p0,phase:e3,estimate:2d" "$E3" "$(cat <<'EOF'
## Description

Vault items must persist envelopes (not plaintext), with `policyId`, `expiresAt`, and `lastRotatedAt` as PRODUCT requires. CRUD already lands behind auth.

## Approach

Preserve thin controllers calling use cases that dogfood `keynest-seal` / policy / generate. No Spring types inside `libs/*`.

## Definition of done

- [x] Authenticated create/read/update/delete
- [x] Passwords stored only as Seal envelopes
- [x] Integration tests cover unauthorized access
- [ ] Update + rotate metadata fields complete relative to PRODUCT domain model

## Estimate
2 workdays (mostly shipped)
EOF
)"

story "[E3] Compose Postgres profile for Manager API" "type:platform,type:api,priority:p0,phase:e3,estimate:1d" "$E3" "$(cat <<'EOF'
## Description

Local H2 is fine for unit speed; the self-host path needs Postgres via Compose as the product default.

## Approach

Keep `deploy/docker-compose.yml` Postgres + API wiring. Document env vars. Schema via JPA/Flyway as already chosen in-tree.

## Definition of done

- [x] `postgres` service in Compose
- [x] Spring docker profile wires datasource
- [ ] README quickstart matches the Compose path

## Estimate
1 workday (mostly shipped)
EOF
)"

story "[E3] Server DEK/KEK from env — no demo hex in prod" "type:api,type:security,priority:p0,phase:e3,estimate:1d" "$E3" "$(cat <<'EOF'
## Description

Compose still injects a fixed `KEYNEST_VAULT_KEY_HEX`. That is acceptable for local demos only if production/cloud profiles refuse to boot without a real secret and never reuse the demo hex.

## Approach

1. Fail fast in production when the key is missing or equals the documented demo value.
2. AAD binds `policyId` (+ user/item ids per Seal docs).
3. Coordinate with E0 tools demo gate so both paths agree.

## Definition of done

- [ ] Production profile rejects demo/missing vault key
- [ ] AAD binding covered by tests or documented vectors
- [ ] Compose local demo path still documented

## Estimate
1 workday
EOF
)"

echo "E4 stories"
story "[E4] Auth screens on Material shell" "type:web,priority:p0,phase:e4,estimate:1d" "$E4" "$(cat <<'EOF'
## Description

Register / login / logout exist inside the monolithic demo. They need Material screens on the E2 shell with brand-first layout.

## Approach

1. Depends on E2 shell.
2. Material inputs; avoid packing secondary chrome into the first viewport.
3. Wire to existing auth API.

## Definition of done

- [ ] Register / login / logout on routed Material screens
- [ ] Session surviving refresh (as API model allows)
- [ ] Brand still readable without nav alone

## Estimate
1 workday
EOF
)"

story "[E4] Vault list + create / reveal / update / delete" "type:web,priority:p0,phase:e4,estimate:2d" "$E4" "$(cat <<'EOF'
## Description

Create / reveal / delete already work in the demo component. Update and a proper list UX on the shell are still required for the PRODUCT vault loop.

## Approach

1. Vault route with list + create/edit/delete.
2. Reveal via authenticated API `open` (server-side).
3. Empty/loading/error via Material patterns from E2.

## Definition of done

- [ ] List + create + reveal + update + delete against Compose API
- [ ] No plaintext password left in client storage beyond explicit reveal state
- [ ] Lives on Material shell

## Estimate
2 workdays
EOF
)"

story "[E4] Generate-on-create and validate-before-save" "type:web,type:generate,priority:p0,phase:e4,estimate:1d" "$E4" "$(cat <<'EOF'
## Description

Manager must dogfood `keynest-generate` / `keynest-policy` the same way external services would — generate on create and validate before save.

## Approach

Wire UI actions to tools endpoints or vault-specific use cases that call the libs. Keep the generate↔validate invariant visible in UX errors.

## Definition of done

- [ ] Generate-on-create path works end-to-end
- [ ] Validate-before-save surfaces structured violations
- [ ] No client-side `Math.random` password generation

## Estimate
1 workday
EOF
)"

story "[E4] Expiry fields + guided rotate action" "type:web,priority:p1,phase:e4,estimate:1d" "$E4" "$(cat <<'EOF'
## Description

Expiry metadata is part of the vault domain model; guided rotate (generate + re-seal) prepares the E5 notify deep link.

## Approach

1. Display `expiresAt` / notify-before fields on create/edit.
2. Rotate action calls generate + re-seal through the API.
3. Leave notification delivery to E5.

## Definition of done

- [ ] Expiry fields visible and persisted
- [ ] Guided rotate updates envelope + `lastRotatedAt`
- [ ] Deep-link target stable for E5

## Estimate
1 workday
EOF
)"

story "[E4] Critical vault flow tests (auth guard + list load)" "type:web,priority:p1,phase:e4,estimate:1d" "$E4" "$(cat <<'EOF'
## Description

ROADMAP asks for at least auth guard + list load coverage so vault regressions are caught without full manual passes.

## Approach

Prefer lightweight component or e2e tests that match the repo’s Angular toolchain. Do not block on a heavy Cypress install if Playwright/Karma already fits.

## Definition of done

- [ ] Auth guard coverage
- [ ] Vault list load coverage
- [ ] Documented `npm test` / e2e command

## Estimate
1 workday
EOF
)"

echo "E5 stories"
story "[E5] Notify outbox + scheduler for approaching expiry" "type:notify,type:api,priority:p0,phase:e5,estimate:2d" "$E5" "$(cat <<'EOF'
## Description

Expiry fields do nothing without a scanner that enqueues due notifications.

## Approach

1. Outbox model (`NotificationOutbox` per PRODUCT).
2. Scheduler scanning `expiresAt` / `notifyBeforeDays` / policy `maxAgeDays`.
3. Idempotent enqueue; do not send email in this story if the adapter is separate.

## Definition of done

- [ ] Due items produce outbox rows
- [ ] No duplicate spam on repeated scans
- [ ] Unit/integration tests for scan logic

## Estimate
2 workdays
EOF
)"

story "[E5] Email adapter (Mailhog/console + cloud config)" "type:notify,type:platform,priority:p0,phase:e5,estimate:1d" "$E5" "$(cat <<'EOF'
## Description

Local demos need Mailhog or console delivery; cloud needs provider config without hardcoding secrets.

## Approach

Adapter interface behind notify; Compose optional Mailhog service; env-based provider settings for cloud.

## Definition of done

- [ ] Local path demonstrates a sent mail without a real ESP
- [ ] Cloud provider settings documented
- [ ] Secrets not logged

## Estimate
1 workday
EOF
)"

story "[E5] UI: upcoming expiry list + rotate deep link" "type:notify,type:web,priority:p0,phase:e5,estimate:1d" "$E5" "$(cat <<'EOF'
## Description

Users must see what is expiring and jump into the E4 rotate flow.

## Approach

Upcoming expiry list in Manager Web; “rotate now” routes into the guided rotate action. Depends on E4 rotate target.

## Definition of done

- [ ] Upcoming expiry list renders due items
- [ ] Rotate deep link opens the rotate flow
- [ ] Empty state when nothing is due

## Estimate
1 workday
EOF
)"

story "[E5] Outbox state machine tests" "type:notify,priority:p1,phase:e5,estimate:0.5d" "$E5" "$(cat <<'EOF'
## Description

Outbox transitions (pending → sent / failed) must be tested so retries do not corrupt state.

## Approach

Table-driven tests around the state machine; failure path marks failed and is retryable per design.

## Definition of done

- [ ] pending → sent / failed covered
- [ ] Retry behaviour documented in test names or short comment

## Estimate
0.5 day
EOF
)"

echo "E6 stories"
story "[E6] Full Compose polish + runbook" "type:platform,type:docs,priority:p0,phase:e6,estimate:1d" "$E6" "$(cat <<'EOF'
## Description

Self-host success criteria require a documented runnable Compose path (API + Postgres + web notes).

## Approach

1. Verify `deploy/docker-compose.yml` brings up API + Postgres.
2. Document Vercel web + Compose API as an accepted topology.
3. Align README quickstart with reality.

## Definition of done

- [ ] Runbook steps work on a clean machine
- [ ] README links the runbook
- [ ] Known gaps (web not in Compose) are explicit

## Estimate
1 workday
EOF
)"

story "[E6] Sample external consumer module" "type:seal,type:policy,type:generate,type:docs,priority:p0,phase:e6,estimate:1d" "$E6" "$(cat <<'EOF'
## Description

PRODUCT success requires an external sample that depends only on seal/policy/generate — proving tools-without-Manager.

## Approach

Add `examples/external-consumer` (Maven) that seals/validates/generates without Manager modules on the classpath.

## Definition of done

- [ ] Example module builds and runs a small main/test
- [ ] No dependency on `manager-api`
- [ ] README points to it as the embeddable proof

## Estimate
1 workday
EOF
)"

story "[E6] README success criteria + publish notes" "type:docs,priority:p1,phase:e6,estimate:0.5d" "$E6" "$(cat <<'EOF'
## Description

Readers should see platform + manager clearly, with honest publish notes (GitHub Packages now; Central later).

## Approach

Update README success criteria to match PRODUCT; add short publish notes without claiming Central.

## Definition of done

- [ ] README states platform + manager in a short read
- [ ] Publish notes for GitHub Packages drafted
- [ ] Links to board + ROADMAP

## Estimate
0.5 day
EOF
)"

story "[E6] Threat model release pass + logging review" "type:security,type:docs,priority:p0,phase:e6,estimate:1d" "$E6" "$(cat <<'EOF'
## Description

Before public release, expand the E0 stub and verify logs never print secrets or envelope plaintext.

## Approach

1. Expand `docs/THREAT_MODEL.md`.
2. Grep/review logging around auth, tools, vault.
3. Confirm rate limits from E0 are still in place.

## Definition of done

- [ ] Threat model expanded for release
- [ ] No secrets/envelope plaintext in logs (review checklist checked)
- [ ] Residual risks listed

## Estimate
1 workday
EOF
)"

story "[E6] GitHub repository rename checklist (operator)" "type:docs,type:platform,priority:p1,phase:e6,estimate:0.5d" "$E6" "$(cat <<'EOF'
## Description

PRODUCT still tracks a manual GitHub rename to `keynest` if the remote name differs. This is an operator checklist, not an app code change.

## Approach

Document Settings → General → Repository name steps and follow-ups (packages, Vercel, Compose docs).

## Definition of done

- [ ] Checklist in docs or README
- [ ] Operator completes rename when ready (may remain open until then)

## Estimate
0.5 day
EOF
)"

echo "Future stories"
story "[Future] MFA (TOTP) after password auth" "type:security,type:api,priority:p2,phase:future,estimate:3d" "$FUT" "$(cat <<'EOF'
## Description

Optional MFA after password success is in PRODUCT’s long-term auth story but not MVP.

## Approach

TOTP first; SMS only as an optional channel later. Rate-limit challenges. Do not start while E0–E6 p0 is open.

## Definition of done

- [ ] ADR for MFA threat model
- [ ] Challenge consume-once semantics
- [ ] Labeled `phase:future` until scheduled

## Estimate
3 workdays
EOF
)"

story "[Future] Downloadable recovery key (vs reset-wipes-vault)" "type:security,type:web,priority:p2,phase:future,estimate:3d" "$FUT" "$(cat <<'EOF'
## Description

MVP locks “reset wipes vault.” A downloadable recovery key is an explicit post-MVP alternative.

## Approach

Design recovery UX + key wrap before coding. Update threat model. No silent change to MVP recovery copy.

## Definition of done

- [ ] ADR choosing recovery-key vs wipe semantics
- [ ] UX copy reviewed
- [ ] Labeled `phase:future`

## Estimate
3 workdays
EOF
)"

story "[Future] Tools HTTP sidecar for non-JVM consumers" "type:api,type:platform,priority:p2,phase:future,estimate:5d" "$FUT" "$(cat <<'EOF'
## Description

PRODUCT defers a tools HTTP sidecar until a non-JVM consumer appears. Keep the ticket so it is not reinvented mid-MVP.

## Approach

Only schedule when a real polyglot consumer exists. Sidecar must call the same Java libs — no second crypto implementation.

## Definition of done

- [ ] Consumer identified
- [ ] Sidecar reuses `keynest-*` libs
- [ ] Labeled `phase:future` until then

## Estimate
5 workdays
EOF
)"

story "[Future] Zero-knowledge client-side open (WebCrypto/WASM)" "type:seal,type:web,priority:p2,phase:future,estimate:5d" "$FUT" "$(cat <<'EOF'
## Description

Manager MVP uses server-side `open`. The envelope should stay client-openable later without a format break.

## Approach

Prove Seal v1 opens with WebCrypto or WASM against golden vectors before changing Manager defaults.

## Definition of done

- [ ] Client open proves against golden vectors
- [ ] ADR for ZK mode threat model
- [ ] No envelope version break

## Estimate
5 workdays
EOF
)"

echo "Done. Next:"
echo "  1. Update docs/reference/board.md with the issue numbers printed above."
echo "  2. Run scripts/setup-github-project.sh with PROJECT_TOKEN to create the Project board."
echo "  3. Close fully shipped stories and any completed epics."
