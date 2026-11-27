# Architecture decisions

An ADR records an accepted decision and its context. Use the [design index](../design/README.md) for proposals and the [threat model](../security/threat-model.md) for current system boundaries.

| Area | Decisions |
| --- | --- |
| Platform and implementation | [0002 Platform and stack](decisions/ADR-0002-platform-and-stack.md), [0005 Toolchain](decisions/ADR-0005-java-and-toolchain-policy.md), [0008 Database migrations](decisions/ADR-0008-versioned-database-migrations.md) |
| Cryptography and key custody | [0001 Crypto model](decisions/ADR-0001-crypto-model.md), [0003 Keynest Seal](decisions/ADR-0003-keynest-seal.md), [0006 Vault key and AAD](decisions/ADR-0006-manager-vault-key-and-aad.md) |
| Password policies and AI | [0004 Policy schema](decisions/ADR-0004-password-policy-schema.md), [0010 Ephemeral BYOK](decisions/ADR-0010-ephemeral-byok-policy-explanation.md), [0011 Account policy](decisions/ADR-0011-account-password-policy.md) |
| Accounts and logs | [0007 Session transport](decisions/ADR-0007-session-token-storage-and-browser-transport.md), [0009 Security events](decisions/ADR-0009-sanitized-http-security-events.md), [0017 Owner-wide sign-out](decisions/ADR-0017-owner-wide-session-revocation.md), [0018 Password change](decisions/ADR-0018-account-password-change.md) |
| Portable backups | [0012 Backup container](decisions/ADR-0012-portable-backup-container.md), [0013 Vault export](decisions/ADR-0013-authenticated-vault-export.md), [0014 Restore foundation](decisions/ADR-0014-native-restore-foundation.md), [0015 Authenticated restore](decisions/ADR-0015-authenticated-native-restore.md), [0016 Restore conflict review](decisions/ADR-0016-restore-conflict-resolution.md) |
