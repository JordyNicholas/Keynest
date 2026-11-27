# Embed the Java libraries

| Library | What it provides |
| --- | --- |
| [`keynest-seal`](../../libs/keynest-seal/) | Versioned AES-256-GCM envelopes, seal/open/rewrap, and Argon2id hashing |
| [`keynest-policy`](../../libs/keynest-policy/) | Schema-backed rule loading and password validation for create, update, rotate, and generate |
| [`keynest-generate`](../../libs/keynest-generate/) | Secure-random generation under those same password rules |

The latest published package is [v0.1.1](../releases/0.1.1.md), distributed as source. Build the libraries with JDK 21 and Maven 3.9 or newer. The [standalone example](../../examples/external-consumer/README.md) provides the install and verification commands. It uses the three public library APIs without starting Manager, PostgreSQL, or a cloud service.

## Contracts

- [Password policy schema](../schemas/password-policy.schema.json) and [default policy](../policies/keynest.default.v1.json)
- [Policy validation decision](../architecture/decisions/ADR-0004-password-policy-schema.md) and [generator category placement](generator-placement.md)
- [Seal envelope, AAD, and buffer handling](../architecture/decisions/ADR-0003-keynest-seal.md)
- [Supported toolchain](../architecture/decisions/ADR-0005-java-and-toolchain-policy.md)

For HTTP clients, see the [Manager API reference](../api/README.md). The Manager uses the same library APIs for its own vault.
