# MVP verification and release evidence

The implementation checkpoint is November 21, 2026, and the public release target is November 28, 2026 (America/Sao_Paulo). Record the release candidate SHA and evidence links before calling a release gate complete. See the [canonical roadmap](./MASTER_ROADMAP.md) for execution order and acceptance criteria.

The maintainer has deferred public deployment. The current goal is to verify that the same code can be deployed across hosting providers. Validate the portable deployment locally and in CI. Rehearse HTTPS ingress, secrets configuration, and recovery on the selected host before an eventual public launch.

| Gate | Evidence required |
| --- | --- |
| Clean install | Fresh packaged Compose startup against PostgreSQL, followed by the local API smoke workflow. |
| Core Manager | Browser walkthrough for sign-in, create, reveal/conceal, rotate, and sign-out with AI disabled. Record keyboard and focus checks. |
| Backup and restore | Restore PostgreSQL and retain the matching vault key. Verify sign-in and reveal after recovery. Record operator rehearsal separately from CI. |
| Library adoption | Build and run the standalone consumer on the candidate. Record artifact availability and supported Java versions. |
| Release documentation | Verify install and operations instructions, license selection, security contact, changelog, and known limitations. |
| BYOK and agent scope | Record mocked BYOK coverage and the live-provider smoke decision. Verify agent permission boundaries for anything included in the candidate. Record any scope deferral explicitly before release. |

External reminder email is off the critical path for this target. The in-app expiry list and local reminder rehearsal are available. Keep external delivery disabled until recipient verification and consent are ready.

## November 23 maintainer verification — native restore and core release paths

**Maintainer-reported result:** pass for the checks listed below, performed at **2026-11-23T18:51:17.463945-03:00** on revision **`fda972f070af68e6d957f3c0d88e29181d23307b`**. This is a Chrome/Ubuntu rehearsal; Windows/Edge were unavailable and are not claimed. The report names one tested revision and a local deployment, not separate Web/API image digests. These results were supplied by the maintainer, not independently rerun by the recording agent.

[Exact-revision main CI](https://github.com/JordyNicholas/Keynest/actions/runs/45213003321) passed on the same SHA. This is a verified implementation/rehearsal checkpoint, not a newly frozen release candidate or public-deployment claim. The older `0.1.0-rc.1` record below remains historical.

| Environment | Reported value |
| --- | --- |
| OS | Ubuntu 24.04.5 LTS, x86_64 |
| Browser | Google Chrome 157.0.8103.50 (Official Build), X11 |
| Docker Engine / Compose | 29.1.3 / 2.40.3 |
| Node / npm | v22.23.4 / 10.9.9 |
| Java | OpenJDK 21.0.13.0.3 |
| PostgreSQL | 16.16, image `postgres:16-alpine` |
| Supported browser origin in this run | `http://localhost:4200` |

| Check | Maintainer-reported result |
| --- | --- |
| Runtime controls | Pass |
| HTTP smoke and CORS | Pass |
| Browser checklist | Pass |
| Mobile layout | Pass |
| Keyboard/focus | Pass |
| Reduced motion | Pass |
| Connection failure/recovery | Pass |
| AI disabled | Pass |
| Backup created and copied | Pass |
| Restore into separate database | Pass |
| Fresh sign-in after restore | Pass |
| Original password matches after restore | Pass |
| Create/reveal after restore | Pass |
| Restore: Update existing | Pass — maintainer follow-up clarification |
| Restore: Keep both | Pass — maintainer follow-up clarification |
| Restore: ambiguous-target selection | Pass — maintainer follow-up clarification |

### Observations and exact limits of this run

1. This run used Chrome on Ubuntu. Windows and Edge were unavailable; no results for those platforms or versions are claimed.
2. The supported origin was `http://localhost:4200`. Opening Web at `http://127.0.0.1:4200` was refused by CORS. This records the exact-origin configuration, not a reason to broaden the allowlist.
3. A burst of restore reviews reached the API limit of 30 requests per minute and returned 429. The same checks passed after the window; limited calls did not change Vault.
4. At 240px the primary navigation scrolled horizontally and Account settings remained reachable. Below 960px the sidebar Sign out control was hidden; Sign out remained on the Account page.
5. At 320px Show password and Copy replacement were side by side; at 240px they stacked. At 390px Vault search and Clear search stacked, while Restore, Export and Add Vault item remained visible.
6. With reduced motion at no preference, one page change animated the main content. With `prefers-reduced-motion: reduce`, the same kind of page change did not.
7. Closing a restore review removed the dialog and wrote nothing. Hiding the tab cleared the selected file and showed “Choose your file and review again after leaving this tab.” The item count was unchanged.
8. Aborting the apply connection produced one attempt and no retry. The dialog warned that restore may have finished and would not be retried automatically. The connection was aborted before server acceptance; the empty Vault remained empty after refresh. **This does not verify a lost response after a successful server commit.**
9. Reimporting the same `.keynest` file into the account already holding its item reviewed as **0 to add, 0 to update, 1 to ignore**. In a follow-up clarification, the maintainer confirmed **Update existing**, **Keep both** and **ambiguous-target selection** all passed in this test run. The separate cross-instance native browser-file scenario is not claimed by that clarification.
10. PostgreSQL recovery used a custom dump of the isolated `keynest-verify` database, restored into a separate database with the same vault key ID. Flyway V1–V6 succeeded; orphan session and Vault counts were zero. Fresh sign-in and the existing-password comparison passed; a new item was created and both items survived an API restart. The original database retained its pre-dump row/password and its volume was kept. Only the disposable database was removed. Successful reveal verifies the retained-key recovery path; no key value is recorded.
11. The production profile rejected the documented demo vault key even with demo-key opt-in set. The process exited 1 without printing the key.
12. Authenticated policy explanation returned `503 AI_DISABLED`; anonymous explanation returned 401; the Docker-profile seal tool returned 403.

### Closeout and remaining release work

Native encrypted export/restore and reviewed duplicate resolution are merged through #227/#231/#235/#241/#245/#249. The reported browser/recovery checks above and their environment are now recorded. These outcomes do not retroactively fill unknown fields in historical rehearsals or establish every individual pending policy/UX test.

Third-party migration is **deferred by the maintainer on November 23** and is not a release prerequisite. Keep destinations unavailable; the pinned Bitwarden parser/model harness (#247) is limited automated evidence, not destination save/sync verification.

**Maintainer release decision: GO**, explicitly confirmed in chat on November 23, 2026, alongside the three additional passing conflict scenarios. This closes the pending maintainer go/no-go decision for the verified scope and documented limitations. It does not manufacture unreported platform/scenario results or waive security gates. Final release preparation still records the chosen version/artifact SHA and its passing CI, with accurate security/limitations and install/distribution documentation. Public hosting and live BYOK remain deferred; the historical `0.1.0-rc.1` candidate is not retagged by this evidence update.

## Verified baseline

[CI for PR #119](https://github.com/JordyNicholas/Keynest/actions/runs/43629011117) passed the packaged API/PostgreSQL smoke over HTTP, the PostgreSQL restore integration test, the Manager Web build and 18 browser tests, and standalone library consumption on JDK 21 and 25. This is baseline evidence. Reconfirm against the final release candidate.

On November 15, [push CI](https://github.com/JordyNicholas/Keynest/actions/runs/43743613231) passed on main commit `158f1042a1ab92d2173d6ab34bb2f2ea203b24f0`. All six jobs passed: Maven and external library consumer on Java 21 and 25, Angular build and tests, and packaged Compose smoke. Repeat CI on the final candidate.

## Implementation baseline for manual verification

On November 16, [main CI](https://github.com/JordyNicholas/Keynest/actions/runs/43889723243) passed all six jobs on `c052de639dbba1856a1bae7e76806122a1637e50`: Maven and standalone consumer on Java 21 and 25, Angular, and packaged Compose smoke. This is historical evidence for the implementation baseline.

On November 18, [PR #163 CI](https://github.com/JordyNicholas/Keynest/actions/runs/44399804190) passed on `d2cdf4571c11a22480f66af6ecb99c0e677b9b1d`. It passed 39 Angular browser tests, Maven and the standalone consumer on Java 21 and 25, and the packaged Compose smoke test. The squash merge [`ee77458`](https://github.com/JordyNicholas/Keynest/commit/ee774580589dd62b4683590ccc89e206d7b296f7) has the same Git tree as the tested PR revision and passed all six jobs in its own [main-branch CI run](https://github.com/JordyNicholas/Keynest/actions/runs/44401800241). Record exact-SHA CI separately when the release candidate is selected.

### Maintainer rehearsal results

JordyNicholas confirmed both results on November 18, 2026. The test dates, revisions, and environments below describe the actual rehearsals.

| Rehearsal | Test date | Tested revision | Environment | Result |
| --- | --- | --- | --- | --- |
| Full-stack browser walkthrough with AI disabled | Pending | Manager Web and API revisions pending | Microsoft Edge on a local workstation. OS, browser version, and Web/API deployment topology pending | Pass, maintainer-confirmed |
| PostgreSQL backup and restore with the matching retained vault key | Pending | Manager API revision or image digest pending | Host/runtime, PostgreSQL version, and restore-target details pending | Pass, maintainer-confirmed |

### November 20 maintainer rehearsal

The maintainer reported this run at **2026-11-20T09:12:59.4248589-03:00**, using revision **`ac03ce592db56ca52719dcaef00b2f6abe7154a1`**. These results do not apply to later policy or UX changes, and do not replace the frozen candidate's revision.

| Environment | Reported value |
| --- | --- |
| OS | Microsoft Windows 11 Home Single Language 10.0.26300, 64 bits |
| Browser | Microsoft Edge 158.0.4370.33 |
| Docker Engine / Compose | 29.9.3 / v5.7.0 |
| Node / npm | v24.14.1 / 11.18.0 |
| PostgreSQL image | `16-alpine` (exact running server version not recorded for this run) |
| Deployment | Local Web and Docker API/PostgreSQL, following the isolated test workflow |

| Check | Maintainer-reported result |
| --- | --- |
| Runtime controls | Pass |
| HTTP smoke and CORS | Pass |
| Browser checklist and mobile layout | Pass |
| Keyboard/focus and reduced motion | Pass |
| Connection failure/recovery, AI disabled | Pass |
| Backup created and copied | Pass |
| Restore into separate database | Pass |
| Fresh sign-in after restore | Pass |
| Original password matches after restore | Pass |
| Create and reveal after restore | Pass |

The run also identified defects tracked in [#192](https://github.com/JordyNicholas/Keynest/issues/192). Registration and sign-in feedback was vague, page errors could be offscreen on mobile, the browser offered to save samples from the checker, and reveal text lacked a live countdown. Most significantly, standalone keyword generation could include characters rejected by Vault. The maintainer selected a shared account policy in [#193](https://github.com/JordyNicholas/Keynest/issues/193), covering generation, validation, creation and rotation with either default or custom rules.

**Follow-up validation pending:** exercise both account policies end to end, keyword generation followed by Vault save, authentication feedback, viewport notifications, checker password-manager behavior in Edge, and timed/manual concealment. Recheck migration and restore after V6. Keep release verification open until fixes have passing CI and the affected human checks are recorded. Historical successful checks remain evidence for their tested revision.

### November 20 policy and UX fix checkpoint

The maintainer approved both changes in chat on November 20. [PR #194](https://github.com/JordyNicholas/Keynest/pull/194) merged the API and V6 account policy migration as `f471507b0a78939d5e488772e599bd11f4e6bb31`. [PR #195](https://github.com/JordyNicholas/Keynest/pull/195) merged the complete browser workflow and UX fixes as **`edd2876d7c3854dc6ff4deaf6b4e092bab8569bb`**.

Before merging, all seven verification jobs passed on each approved PR head. [The complete workflow run](https://github.com/JordyNicholas/Keynest/actions/runs/44746480788) tested `921cb260b1876d1f5b0b210ad7fb944ab3043e90`, including 46 browser tests, 29 API URL configuration tests, Maven and independent library consumers on Java 21/25, PostgreSQL migration/restore tests, and packaged/hosted Compose smoke tests. The merged code has the same Git tree, `853978c6d6085a95c583c1efd9ef01fcbb2517f3`, verified by a clean Git diff. [Exact merged-revision CI](https://github.com/JordyNicholas/Keynest/actions/runs/44754783163) passed all seven verification jobs. Its first attempt encountered a Maven Central plugin-resolution failure in the Java 25 consumer job; the subsequent attempt passed. Vercel also reported success on this merged revision. The Angular build reports a nonblocking initial bundle warning of about 572 kB against its 500 kB warning threshold.

| Targeted human recheck on the merged revision | Result |
| --- | --- |
| Default policy: standalone keyword generation → check → Vault save → reveal | Pending |
| Custom account policy: generate, check, create and rotate use the saved selection | Pending |
| Changing account policy preserves older secrets; an open stale form reports the changed selection | Pending |
| Invalid email, short master password and incorrect sign-in show useful feedback | Pending |
| Mobile error notification is visible without scrolling and can be dismissed | Pending |
| Edge checker avoids unwanted save-password prompts | Pending |
| Reveal countdown decreases and clears on automatic/manual concealment and navigation | Pending |
| V6 upgrade and retained-key restore preserve sign-in, account policy and readable secrets | Pending |

Record the actual tested revision and environment for this follow-up. The November 20 morning rehearsal remains valid evidence for its original revision. This checkpoint does not create a new candidate, waive human checks or enable public deployment or AI expansion.

### Subsequent November 20 UX feedback

The maintainer reported most earlier fixes working and identified a remaining persistent inline error. No new tested SHA or item-by-item follow-up results were supplied, so this confirmation does not close every pending check above. [PR #198](https://github.com/JordyNicholas/Keynest/pull/198) refined the inline alert with a close control, a five-second lifetime paused during inline pointer/focus interaction, and coordinated clearing of the bottom notification and parent error. All seven [PR verification jobs](https://github.com/JordyNicholas/Keynest/actions/runs/44801245223) passed, including 49 browser tests, before merge as `bb2efa254c5d4c64f701ac5b120206091ff850e6`. Human confirmation of the new dismissal behavior remains pending.

The maintainer also requested a complete, tailored homepage/account-overview design without rushing. [The design proposal](design/home-overview/DESIGN.md) records this work. Its synthetic prototype is not an implementation or release-validation result. Production routing and new generation/history tracking remain subject to feature review.

### Operator environment captured November 18, 2026

| Command | Reported output |
| --- | --- |
| `git rev-parse HEAD` | `3f10d42f7a58b726d108b712d8fac00287356599` |
| `docker compose version` | `Docker Compose version v5.7.0` |
| `postgres --version` in the Compose PostgreSQL service | `postgres (PostgreSQL) 16.16` |

The checkout SHA matches the frozen candidate. Attribution of this checkout and environment to the historical walkthrough and restore is pending.

### Deployment portability

The maintainer reported successful testing with both local instances. The exact test revisions are pending. A Vercel browser run against the local Docker API remains optional for internal verification.

[Main-branch CI](https://github.com/JordyNicholas/Keynest/actions/runs/44432591629) passed all seven verification jobs on `cbffb909162f5cc01b9726c53872dba005cf4241`. It exercised both local and production-profile Docker Compose with PostgreSQL, including the hosted profile's allowed and denied CORS checks. Angular built with both a relative API path and a configured HTTPS API URL. This run verifies the portable deployment configuration on `main`. The frozen candidate has its own CI evidence below.

### Evidence to record

Record the revision, date, operator and environment, observed result, and sanitized evidence for each rehearsal. Use pass, fail, or pending.

For a local build, run `git rev-parse HEAD` in the checkout used to build the tested service. If the checkout has moved since the test, use the original build or deployment record. Record Web and API separately when they came from different builds. For a hosted deployment, use its recorded source commit, deployment ID, or image digest. Use **Unknown** when the original evidence cannot be recovered.

- **Browser:** Exercise sign-in, both manual and generated creation, reload, reveal/conceal, metadata edit, rotation, delete confirmation, and sign-out. Include keyboard focus, reduced motion, and a rejected password.
- **Recovery:** Follow [database migrations](./DATABASE_MIGRATIONS.md) using a disposable restore target and the separately retained matching vault key. Verify fresh sign-in and reveal after restart.
- **Board:** Track shipping in [#12](https://github.com/JordyNicholas/Keynest/issues/12). The repository rename in [#44](https://github.com/JordyNicholas/Keynest/issues/44) is an operator task and is outside this verification run.

## Full-stack browser rehearsal

Use the disposable API/PostgreSQL and separate Manager Web setup in [Compose](./COMPOSE.md). Leave AI and reminder dispatch disabled. Use synthetic accounts and secrets.

1. Open Manager Web on port 4200. Review the anonymous product homepage, open signup/sign-in in the shared dialog and check close/Escape/focus return. Register a synthetic account if needed, then sign in and verify `/overview` shows the real empty/populated account state. Test the direct Account fallback and a guarded Vault deep link. Tab through navigation, metrics and actions and verify visible focus and a usable tab order. Use the [overview checklist](ACCOUNT_OVERVIEW.md#states-and-verification) for the new design.
2. From Vault, open **Add item**. Save one entry with **Generate on save** using a custom length and a synthetic keyword. Check that the form marks the character types the Vault policy requires. Save another entry with **Enter an existing password**. Try a deliberately weak synthetic password and verify policy feedback. Reload Vault and verify the saved entries, expiry dates and remaining-time labels.
3. Expand one Vault item and verify expansion alone never reveals a password. Reveal it, check the live conceal countdown, conceal manually, then reveal again and verify automatic concealment after 15 seconds. Change page/filter or collapse during a pending reveal and verify no late value appears.
4. Open **Edit details**, change the label or username, and save. Open **Rotate password** for the same item, verify **Generator options** starts collapsed, then open it and generate a replacement using **Use Vault policy**. Choose **Customize generation**, set a custom length and synthetic keyword, generate another replacement, verify **Show password** and **Hide password**, copy the replacement, then confirm saving it for the synthetic account. Expand the Vault item to find editing, rotation and deletion in its action grid, including on mobile. Verify **Password last set** changes. Reveal once to check the replacement, then conceal it.
5. Exercise 10/25/30/50-item pages, metadata search combined with expiry filters, clear-search behavior and selected-item links. Cancel a deletion and verify the item remains. Confirm deletion and verify the item disappears and the last page/counts reconcile. Refresh the overview and check its totals/groups/current-item list.
6. Sign out and revisit Vault and the rotation link. Verify both require sign-in. Repeat key navigation with the browser's reduced-motion preference enabled.
7. Open **Check password policy**. Type a synthetic sample and verify the meter and checklist update without a validation request. Confirm the selected account check with **Check password**. In Account settings, save custom password rules and reopen generation, checking and Vault rotation to verify all use the same rules. Guest-only temporary sample rules do not change account settings.
8. Record the tested SHA, environment, date and outcomes. Keep credentials and revealed values out of the evidence.

## Governance evidence

[PR #124](https://github.com/JordyNicholas/Keynest/pull/124) added the Apache-2.0 license, Maven license metadata and security policy. The maintainer confirmed on November 15 that GitHub private vulnerability reporting is enabled. The maintainer deferred the reporter-side form check. See the [security policy](../SECURITY.md).

The [candidate changelog](../CHANGELOG.md) is in place.

## Candidate distribution

Keynest `0.1.0-rc.1` is pinned to [`3f10d42`](https://github.com/JordyNicholas/Keynest/commit/3f10d42f7a58b726d108b712d8fac00287356599). All six jobs in [the candidate CI run](https://github.com/JordyNicholas/Keynest/actions/runs/44404840850) passed on that exact commit, including 39 Angular browser tests, Maven and the standalone consumer on Java 21 and 25, and packaged Compose smoke.

The `0.1.0-rc.1` distribution path is a checkout or source archive of the candidate commit. The [standalone consumer](../examples/external-consumer/README.md) verifies local installation of the three Java libraries on Java 21 and 25. The [Compose guide](./COMPOSE.md) builds the Manager API from the same source, and the [README](../README.md) covers Manager Web. GitHub Packages publication is planned separately.

## Open release gates

- Use the dated November 23 checkpoint for its explicitly reported Chrome/Ubuntu browser, runtime and PostgreSQL recovery results. Older rehearsals retain their unknown fields; do not infer platform coverage or unreported individual checks.
- Update existing, Keep both and ambiguous-target selection are now maintainer-confirmed passes for the November 23 run. Retain platform/scenario limits and unresolved individual historical checks honestly; resolve any release-blocking defect before publishing.
- Maintainer release go is recorded above. Finalize security/limitations, clean-install and source-distribution documentation; record the final version/artifact SHA and passing exact-SHA CI before tag/publication.
- Third-party migration and public hosting are deferred. Optional live BYOK rehearsal requires maintainer approval; keep BYOK disabled for core verification. Agent tooling is post-release.
