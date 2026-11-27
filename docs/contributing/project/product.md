# Keynest product scope

Keynest serves end users who want to self-host their Vault and developers who want to embed password generation, policy checks, or authenticated encryption.

## Two offerings

| Offering | Scope |
| --- | --- |
| Keynest Manager | Angular web UI and Spring Boot API for account sessions, owner-scoped Vault items, policy settings, expiry views, and native file backup and restore |
| Keynest libraries | Independent Java libraries for policy validation, secure random generation, and versioned Seal envelopes with Argon2id hashing |

Manager uses the same library APIs that integrations use. The [standalone consumer](../../../examples/external-consumer/README.md) verifies that the libraries can run independently. Published tags are source packages. See the [release notes](../../releases/README.md) for versioned behavior and the [changelog](../../../CHANGELOG.md#unreleased) for changes on `main`.

## Manager workflows

- Add, find, reveal, edit, rotate, delete, and set expiry for a saved login. Rotation stores a replacement after the password has been changed with the external service.
- Select default or custom password rules shared by signed-in tools, new Vault passwords, and replacements.
- Export up to 500 selected items in an authenticated encrypted `.keynest` file and restore through review and explicit confirmation.
- Generate or check passwords locally on the public homepage. Signed-in tools use the selected account policy.
- Enable the optional read-only AI explanation of the built-in policy through operator configuration and an ephemeral user-supplied provider key.

See the [Manager guide](../../manager/README.md) for user workflows and [deployment](../../operators/README.md) for supported configurations.

## Security requirements (non-negotiable)

The server decides authentication, ownership, policy validity, and allowed operations. Keynest uses standard cryptographic primitives and keeps production vault keys separate from database backups. Core features must work with AI disabled. Passwords, keys, and raw session bearers must stay out of durable logs and telemetry. The [threat model](../../security/threat-model.md) records the trust boundaries and operational limits. [Accepted decisions](../../architecture/README.md) define the technical contracts.

## Planned and deferred work

Third-party import/export destinations, verified email and OIDC, MFA, recovery, durable activity history, richer AI workflows, and an Agent Gateway remain separate designs or deferred work. Use the [canonical roadmap](roadmap.md) for scope, dependencies, and gates. The [original grooming record](../../archive/product-grooming.md) preserves early stack and naming discussion.
