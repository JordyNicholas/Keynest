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
