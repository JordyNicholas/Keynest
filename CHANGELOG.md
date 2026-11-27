# Changelog

## Unreleased

- Randomize Java-generated password category positions with Fisher–Yates before policy validation, preserving requirements, exclusions and bounded retries.

- Add current-password-confirmed account password change with atomic hash/session replacement, preserved Vault data, and fresh sign-in handling for uncertain responses.

- Add password-confirmed sign-out across all account sessions, including the current browser, with coordinated sign-in issuance, no automatic retry and unchanged Vault data.

- Clean expired sessions in bounded scheduled batches, preserving active sessions; add the V7 expiry index and documented upgrade/worker limits.

- Add central default-deny API authentication and explicit handler-access inventory, retaining public/optional tool contracts, owner checks, CORS and bulk-operation reauthentication.

- Add bounded per-process sign-in failure/cooldown admission by normalized email across client IPs, with in-flight serialization and generic retry guidance. Document targeted-lockout, full-table, restart and replica limits.

- Remove the unknown-account login verification shortcut by using one prepared dummy hash through the existing Argon2 capacity budget; per-account throttling and registration enumeration remain separate hardening work.

- Bind production Web CSP to the compiled API destination, add mandatory Web framing/referrer/MIME response headers and verify emitted policies with browser framing and real split-origin API tests.

- Apply a shared API response-header baseline across successful, rejected, preflight and servlet error responses; preserve cache/CORS behavior and document operator-managed HTTPS/HSTS. Web CSP and browser session transport remain separate hardening work.
- Add dry-run-first source-release preparation with metadata/inventory/gate validation and explicit owner-dispatched publication of new tags and verified assets. See [operator sequence and recovery limits](docs/SOURCE_RELEASE.md).
- Pin existing CI/interoperability actions to verified commits, disable persisted checkout credentials and add a weekly source dependency scan; existing runtime gates and explicit publication behavior remain.

## 0.1.1 — 2026-11-24

Security dependency maintenance release; source distribution. See [setup, upgrade, verification and limitations](docs/releases/0.1.1.md). The maintainer approved publication after final-source checks.

- Update Spring Boot 4.0.10, Jackson 2/3 managed versions and embedded Tomcat 11.0.29.
- Update Angular 21 framework/compiler to 21.2.26 and refresh compatible build dependencies.
- Patch the remaining glob/minimatch/uuid development dependencies through scoped overrides, with five actual-consumer compatibility checks.
- Record the maintenance risk review and clarify that Seal v1 header key IDs are unauthenticated metadata; no encryption format or custody change.
- Add a separate read-only dependency inventory/vulnerability-check workflow with source-linked CycloneDX Java/Web inventories, validated graph coverage and a pinned OSV scanner. See [scope, artifacts and triage](docs/DEPENDENCY_SECURITY.md).

No data/schema, session, key, API or native backup-format migration is introduced. Existing architectural and deployment limitations remain. Complete final-source CI and dependency matching are required; no independent audit or vulnerability-free claim.

## 0.1.0 — 2026-11-23

First source-based MVP release. Includes the libraries and Manager described under the historical `0.1.0-rc.1` checkpoint, plus the changes below. See [release notes, setup and limitations](docs/releases/0.1.0.md).

- Select default or custom account password rules shared by generation, checking, new Vault items and rotation. The V6 migration retains default rules for existing accounts and keeps saved secrets readable. See [account password policy](./docs/ACCOUNT_PASSWORD_POLICY.md).
- Apply ambiguity, blocked-string and repeated-character rules to browser generation, including transformed keywords.
- Clarify account validation and API failures, display dismissible viewport error notifications, explain policy restrictions, and show a live reveal countdown.
- Reduce login-form heuristics on the sample checker. Edge password-manager behavior and the other affected human checks remain pending in [release evidence](./docs/MVP_RELEASE_EVIDENCE.md#november-20-policy-and-ux-fix-checkpoint).

- Introduce the public homepage with collapsed generator/checker tools, shared sign-in dialog, guarded account workspace, metadata overview and bounded Vault search/browsing.
- Add localized calendar-based expiry entry and a Show/Hide control to the public checker.

### Native backup and restore

- Export all or selected Vault items as a passphrase-protected `.keynest` file and restore through an authenticated review/apply workflow.
- Review duplicates before restoring: identical entries default to Ignore; changed matches offer Update existing, Ignore or Keep both. Ambiguous matches require an explicit receiving target, and updates require replacement acknowledgment.
- Native files restore items, not accounts or an entire installation. Full recovery still requires PostgreSQL backups and the separately retained matching vault key.

### November 23 verification and release decision

The maintainer reported **PASS** for **Update existing**, **Keep both** and **ambiguous-target selection**, supplementing the Chrome/Ubuntu rehearsal at `2026-11-23T18:51:17.463945-03:00` on `fda972f070af68e6d957f3c0d88e29181d23307b`. Browser/runtime/accessibility, AI-disabled operation and separate-database retained-key recovery also passed as recorded in [release evidence](./docs/MVP_RELEASE_EVIDENCE.md#november-23-maintainer-verification--native-restore-and-core-release-paths).

**Maintainer release decision: GO**, confirmed in chat on November 23, 2026, for the verified scope and documented limitations. The `0.1.0` source release preserves the historical `0.1.0-rc.1` checkpoint. Final source/tag and CI provenance are recorded in the GitHub Release.

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
