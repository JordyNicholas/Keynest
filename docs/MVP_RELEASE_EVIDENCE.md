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

1. Open Manager Web on port 4200. Register a synthetic account if needed, then sign in from Account. Tab through sign-in and vault actions and verify visible focus and a usable tab order.
2. From Vault, open **Add item**. Save one entry with **Generate on save** using a custom length and a synthetic keyword. Check that the form marks the character types the Vault policy requires. Save another entry with **Enter an existing password**. Try a deliberately weak synthetic password and verify policy feedback. Reload Vault and verify the saved entries, expiry dates and remaining-time labels.
3. Reveal the password, conceal it manually, then reveal again and verify automatic concealment after 15 seconds.
4. Open **Edit details**, change the label or username, and save. Open **Rotate password** for the same item, verify **Generator options** starts collapsed, then open it and generate a replacement using **Use Vault policy**. Choose **Customize generation**, set a custom length and synthetic keyword, generate another replacement, verify **Show password** and **Hide password**, copy the replacement, then confirm saving it for the synthetic account. On mobile, open the card’s **More actions** menu to find editing, rotation and deletion. Verify the rotation timestamp changes. Reveal once to check the replacement, then conceal it.
5. Cancel a deletion and verify the item remains. Confirm deletion and verify the item disappears.
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

- Complete the test date, revision, and environment fields for the maintainer-confirmed rehearsals.
- Optional live BYOK rehearsal requires maintainer approval. Keep BYOK disabled for core verification. Agent tooling is post-release.
