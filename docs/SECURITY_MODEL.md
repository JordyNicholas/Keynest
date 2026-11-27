# Keynest security model and limits

Read this before using real credentials. Keynest Manager is a **server-managed vault**: it decrypts saved passwords on the API server. Trust the machine/operator running it and the browser/device you use. It is not a client-side zero-knowledge design or an independently audited/compliance-certified service.

This page describes the shared current design boundaries. Main also contains unreleased changes after the published [v0.1.1 source release](releases/0.1.1.md); use that release's setup/limitations for its exact scope. Proposals in the engineering roadmap are not product capabilities.

## Public tools and Manager data

| Workflow | What happens to the value |
| --- | --- |
| Homepage Generate/Check | Fixed packaged default rules run in the browser. Generated/sample values are not sent to the API or browser storage by these tools. Copy writes to the device clipboard when requested. Closing/switching the panel or hiding/leaving the page clears tool state. |
| Signed-in Vault | Saving, revealing, rotating, exporting or restoring involves the API. Saved passwords use AES-256-GCM encryption under the installation's server-wide vault key; the server can decrypt them. Label, URL, username, policy and expiry metadata are plaintext database fields. |
| HTTP developer tools | Submitted hash/verify/validation inputs reach the API. The homepage tools' local-only property does not apply to arbitrary HTTP tool calls or signed-in Vault operations. |

The public checker gives locally checked policy feedback, not password-strength certification; some Unicode checks remain unconfirmed locally. Final Vault validation uses the authenticated account policy. See [public tool behavior](PUBLIC_PASSWORD_TOOLS.md). Clearing page state does not guarantee erasure of browser/JVM copies, clipboard history, extensions, screen captures or destination applications.

## Account and host trust

Account passwords are verified using Argon2id; the database stores their hashes. Session tokens are opaque bearers, with only their digests stored server-side. The browser retains its bearer in tab-scoped `sessionStorage`, so same-origin JavaScript/XSS or a compromised device can steal it. Keep the web application, API host and dependencies trusted; HTTPS, origin/proxy configuration and browser response headers are deployment responsibilities. Server-managed encryption does not protect a vault from its compromised operator/process.

The application enforces owner/session access and deterministic policy checks. Rate/admission limits and short-lived restore state are process-local: restarts and replicas affect their behavior, and occupying bounded capacity can deny other users. Native restore review/apply must reach the appropriate process and may require a new review after restart/expiry. These controls do not guarantee service availability or provide distributed enforcement.

## Before using real data

The local Compose sample deliberately uses public demo database credentials/key and loopback host ports. Use synthetic data there. For real deployment, follow the [deployment guide](DEPLOYMENT.md) to configure a distinct non-demo vault key/key ID, private database credentials, the production profile, exact browser origins, trusted direct proxies and HTTPS ingress. Preserve the same database and key across updates. The production profile rejects the documented demo vault key.

Manager Web runs separately from the API/PostgreSQL Compose stack. A successful local rehearsal or CI run does not verify your own public hostname, TLS/proxy topology, key custody or backups. Use [operations](OPERATIONS.md) and [availability guidance](design/security/AVAILABILITY_CONTROLS.md) for those boundaries.

## Backups and account access

| Backup | Recovery boundary |
| --- | --- |
| Passphrase-protected `.keynest` file | Restore supported Vault items into a signed-in receiving account using the file passphrase and the review flow. Review ignore/update/keep-both choices; ambiguous targets require explicit resolution. This is not a backup of account credentials, sessions or the full installation. |
| PostgreSQL backup plus retained vault key/key ID | Recover the installation's database and decrypt its existing envelopes with the matching key. A database dump alone cannot recover the encrypted passwords if that key is lost. |

Keep backup passphrases/key material separately from their backups and support reports. A file passphrase cannot be reset; changing the account password is not a way to recover a lost file passphrase or server key. Retain the pre-upgrade database and application version when a migration makes application-only rollback insufficient. Follow the [native restore guide](VAULT_RESTORE_WEB.md#using-a-keynest-file) and [database recovery runbook](DATABASE_MIGRATIONS.md), and rehearse recovery with synthetic/isolated data.

If a mutation's connection fails, it may already have committed. Do not automatically replay an uncertain restore; refresh and review its documented recovery path. Canceling a request does not reverse a server commit.

## Current limits

AI is optional and disabled by default. The limited authenticated policy-explanation backend uses an ephemeral supplied provider key and explicit operator/provider configuration; enabling it involves third-party processing. Live-provider verification and the browser workflow remain pending in [the AI backend contract](AI_BACKEND.md). Core Vault/tools need no AI key. No raw Vault password is an input to that built-in policy explanation.

Verified-email activation, external reminder delivery, OIDC/SSO, MFA, password recovery, team sharing, shared rate limiting, keyring/KMS custody and agent capabilities remain separate work; this onboarding path does not enable them. Main's newer hardening controls must not be relabelled as published v0.1.1 features. Source distribution does not imply Maven registry availability or a hosted service.

Libraries expose standard primitives (AES-256-GCM, Argon2id and CSPRNG-based generation), with consumer-owned authorization, key custody and context/AAD rules. Seal v1 key-ID headers are not automatically authenticated; do not use an untrusted header as authorization or a trusted key selector. See [Seal's contract and buffer limits](ADR-0003-keynest-seal.md) and the [independent example](../examples/external-consumer/README.md).

The detailed [threat model](THREAT_MODEL.md) records remaining risks. Report suspected vulnerabilities privately through [SECURITY.md](../SECURITY.md), using synthetic reproduction steps and excluding real keys, tokens, credentials, dumps and Vault contents from public issues.
