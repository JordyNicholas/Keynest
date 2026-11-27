# Contributing to Keynest

Start with [CONTRIBUTING.md](../../CONTRIBUTING.md) for development setup and local commands.

- [Project charter](project/charter.md) and [product scope](project/product.md) explain the purpose and boundaries.
- [Canonical roadmap](project/roadmap.md) covers sequencing, release gates, and proposed work. See [the board reference](project/board.md) for issue templates and tracking conventions.
- [Pull requests](pull-requests.md) covers branches, verification, and approval. [AGENTS.md](../../AGENTS.md) contains repository instructions for engineering agents.
- [Coding standards](coding-standards.md) covers source conventions and local checks. [Quality gates](quality/README.md) explains what CI enforces.
- [Source releases](source-releases.md) covers version preparation, dry runs, owner dispatch, and partial-failure recovery.

## Repository layout

| Path | Responsibility |
| --- | --- |
| [`libs/keynest-policy`](../../libs/keynest-policy/) | Schema-backed password policy and lifecycle validation |
| [`libs/keynest-generate`](../../libs/keynest-generate/) | Policy-based password generation |
| [`libs/keynest-seal`](../../libs/keynest-seal/) | Authenticated encryption and Argon2id |
| [`apps/manager-api`](../../apps/manager-api/) | Spring Boot API and PostgreSQL-backed Vault |
| [`apps/manager-web`](../../apps/manager-web/) | Angular Manager |
| [`deploy`](../../deploy/) | API image, Compose and Web hosting assets |
| [`examples/external-consumer`](../../examples/external-consumer/) | Standalone Java library consumer |

## Run checks

Use the [Java and Web toolchain policy](../architecture/decisions/ADR-0005-java-and-toolchain-policy.md). From the root, run `mvn test`. For frontend tests, production builds, and browser verification, follow the [Manager Web development guide](../../apps/manager-web/README.md). The [local setup runbook](../operators/local-setup.md#disposable-http-smoke-test) covers the disposable packaged API smoke test.
