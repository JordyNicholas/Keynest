# November 21 MVP release evidence

Target: Saturday, November 21, 2026 (America/Sao_Paulo). Record the release candidate SHA and evidence links before calling a gate complete. See the [canonical roadmap](./MASTER_ROADMAP.md) for execution order and acceptance criteria.

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

On November 16, [main CI](https://github.com/JordyNicholas/Keynest/actions/runs/43889723243) passed all six jobs on `c052de639dbba1856a1bae7e76806122a1637e50`: Maven and standalone consumer on Java 21 and 25, Angular, and packaged Compose smoke. Use this revision to start manual verification. Select the release candidate after verification and any fixes.

### Evidence to record

Record the revision, date, operator and environment, observed result, and sanitized evidence for each rehearsal. Use pass, fail, or pending.

- **Browser:** Exercise sign-in, both manual and generated creation, reload, reveal/conceal, metadata edit, rotation, delete confirmation, and sign-out. Include keyboard focus, reduced motion, and a rejected password.
- **Recovery:** Follow [database migrations](./DATABASE_MIGRATIONS.md) using a disposable restore target and the separately retained matching vault key. Verify fresh sign-in and reveal after restart.
- **Board:** Track shipping in [#12](https://github.com/JordyNicholas/Keynest/issues/12). The repository rename in [#44](https://github.com/JordyNicholas/Keynest/issues/44) is an operator task and is outside this verification run.

## Full-stack browser rehearsal

Use the disposable API/PostgreSQL and separate Manager Web setup in [Compose](./COMPOSE.md). Leave AI and reminder dispatch disabled. Use synthetic accounts and secrets.

1. Open Manager Web on port 4200. Register a synthetic account if needed, then sign in from Account. Tab through sign-in and vault actions and verify visible focus and a usable tab order.
2. From Vault, open **Add item**. Save one entry with **Generate on save** using a custom length and a synthetic keyword. Check that the form marks the character types the Vault policy requires. Save another entry with **Enter an existing password**. Try a deliberately weak synthetic password and verify policy feedback. Reload Vault and verify the saved entries, expiry dates and remaining-time labels.
3. Reveal the password, conceal it manually, then reveal again and verify automatic concealment after 15 seconds.
4. Open **Edit details**, change the label or username, and save. Open **Rotate password** for the same item, generate a replacement, copy it from the masked input, then confirm saving it for the synthetic account. Verify the rotation timestamp changes. Reveal once to check the replacement, then conceal it.
5. Cancel a deletion and verify the item remains. Confirm deletion and verify the item disappears.
6. Sign out and revisit Vault and the rotation link. Verify both require sign-in. Repeat key navigation with the browser's reduced-motion preference enabled.
7. Open **Check password policy**. Type a synthetic sample and verify the meter and checklist update without a validation request. Confirm the built-in check with **Check password**, then switch to custom rules and verify changing a rule updates the result without sending the sample.
8. Record the tested SHA, environment, date and outcomes. Keep credentials and revealed values out of the evidence.

## Governance evidence

[PR #124](https://github.com/JordyNicholas/Keynest/pull/124) added the Apache-2.0 license, Maven license metadata and security policy. The maintainer confirmed on November 15 that GitHub private vulnerability reporting is enabled. The maintainer deferred the reporter-side form check. See the [security policy](../SECURITY.md).

The [unreleased changelog](../CHANGELOG.md) is in place.

## Open release gates

- Full-stack rehearsal and operator backup/restore with separately retained vault key.
- Package availability.
- Optional live BYOK rehearsal requires maintainer approval. Keep BYOK disabled for core verification. Agent tooling is post-release.
