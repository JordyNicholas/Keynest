# November 21 MVP release evidence

Target: Saturday, November 21, 2026 (America/Sao_Paulo). Record the release candidate SHA and evidence links before calling a gate complete. See the [delivery plan](./DELIVERY_PLAN.md) for original acceptance criteria.

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

## Full-stack browser rehearsal

Use the disposable API/PostgreSQL and separate Manager Web setup in [Compose](./COMPOSE.md). Leave AI and reminder dispatch disabled. Use synthetic accounts and secrets.

1. Open Manager Web on port 4200. Sign in from Account. Tab through sign-in and vault actions and verify visible focus and a usable tab order.
2. Create a generated vault entry, reload Vault, and verify the entry and expiry metadata appear.
3. Reveal the password, conceal it manually, then reveal again and verify automatic concealment after 15 seconds.
4. Open the rotation editor, generate a replacement, and save it for the synthetic account. Verify the rotation timestamp changes. Reveal once to check the replacement, then conceal it.
5. Sign out and revisit Vault and the rotation link. Verify both require sign-in. Repeat key navigation with the browser's reduced-motion preference enabled.
6. Record the candidate SHA, environment, date and outcomes. Keep credentials and revealed values out of the evidence.

## Governance evidence

[PR #124](https://github.com/JordyNicholas/Keynest/pull/124) added the Apache-2.0 license, Maven license metadata and security policy. The maintainer confirmed on November 15 that GitHub private vulnerability reporting is enabled. A signed-in non-admin still needs to verify that **Report a vulnerability** opens the private form. A submission is unnecessary. See the [security policy](../SECURITY.md).

The [unreleased changelog](../CHANGELOG.md) is in place.

## Open release gates

- Full-stack rehearsal and operator backup/restore with separately retained vault key.
- Package availability and a reporter-side check of GitHub's private vulnerability form.
- Maintainer decision on live BYOK rehearsal and agent scope for this release.
