# Changelog

## Unreleased

Changes on `main` after the frozen `0.1.0-rc.1` candidate. These changes do not retag that candidate.

- Select default or custom account password rules shared by generation, checking, new Vault items and rotation. The V6 migration retains default rules for existing accounts and keeps saved secrets readable. See [account password policy](./docs/ACCOUNT_PASSWORD_POLICY.md).
- Apply ambiguity, blocked-string and repeated-character rules to browser generation, including transformed keywords.
- Clarify account validation and API failures, display dismissible viewport error notifications, explain policy restrictions, and show a live reveal countdown.
- Reduce login-form heuristics on the sample checker. Edge password-manager behavior and the other affected human checks remain pending in [release evidence](./docs/MVP_RELEASE_EVIDENCE.md#november-20-policy-and-ux-fix-checkpoint).

### Native backup and restore

- Export all or selected Vault items as a passphrase-protected `.keynest` file and restore through an authenticated review/apply workflow.
- Review duplicates before restoring: identical entries default to Ignore; changed matches offer Update existing, Ignore or Keep both. Ambiguous matches require an explicit receiving target, and updates require replacement acknowledgment.
- Native files restore items, not accounts or an entire installation. Full recovery still requires PostgreSQL backups and the separately retained matching vault key.

### November 23 verification and release decision

The maintainer reported **PASS** for **Update existing**, **Keep both** and **ambiguous-target selection**, supplementing the Chrome/Ubuntu rehearsal at `2026-11-23T18:51:17.463945-03:00` on `fda972f070af68e6d957f3c0d88e29181d23307b`. Browser/runtime/accessibility, AI-disabled operation and separate-database retained-key recovery also passed as recorded in [release evidence](./docs/MVP_RELEASE_EVIDENCE.md#november-23-maintainer-verification--native-restore-and-core-release-paths).

**Maintainer release decision: GO**, confirmed in chat on November 23, 2026, for the verified scope and documented limitations. This records the decision; it does not retag the historical `0.1.0-rc.1` candidate or claim an artifact publication.

Windows/Edge, a separate browser-file cross-instance round trip and a lost response after server commit are not claimed by this rehearsal. Third-party migration and public hosting remain deferred; BYOK remains disabled for core verification. Final release artifacts retain exact-SHA CI and version/provenance requirements.

## 0.1.0-rc.1

First Keynest MVP release candidate. Build Manager and the Java libraries from source.

### Libraries

- Embed password generation, policy validation, and encrypted envelopes through the standalone Java libraries. The [external consumer example](./examples/external-consumer/README.md) builds independently of Manager on Java 21 and 25.

### Manager

- Create, list, reveal, edit, and delete vault items. Manager Web automatically conceals revealed passwords. Vault cards show the time remaining to expiry and place secondary actions in a menu.
- Generate passwords in the browser with optional length, character-type, and keyword settings. The policy checker shows which rules the sample meets as you type.
- Rotate a saved password with the Vault policy or custom generation settings bounded by that policy. Show, hide, or copy a replacement before confirming the save.
- The optional BYOK workflow opens key setup in Account settings and displays the explanation on the built-in policy page. AI is disabled by default. See [BYOK policy explanations](./docs/AI_BACKEND.md) for configuration, data handling, and verification status.
- Local reminder rehearsals can simulate delivery without an email provider. External reminder email remains unavailable.

### Deployment and upgrades

- Packaged local setup uses PostgreSQL and a separately started Manager Web. Follow the [Compose guide](./docs/COMPOSE.md) for prerequisites and local configuration.
- Flyway manages schema changes. Back up the database and retain the matching vault key before upgrading. See [database migrations](./docs/DATABASE_MIGRATIONS.md) and [operations](./docs/OPERATIONS.md).
- Release verification and outstanding gates are tracked in the [MVP checklist](./docs/MVP_RELEASE_EVIDENCE.md).
