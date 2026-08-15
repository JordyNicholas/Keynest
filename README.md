# Keynest

Password **tools** you can embed — and a **Manager** vault that uses them.

| Offering | Role |
|---|---|
| **Keynest Tools** | Java libraries: Seal (encrypt/decrypt), policy validate, generate |
| **Keynest Manager** | Angular + Spring Boot product: vault CRUD, expiry, auth/MFA |

Self-host or cloud. External services depend on the same libraries the Manager dogfoods.

> Formerly `fep-password`. North-star grooming: [docs/PRODUCT.md](./docs/PRODUCT.md).

## Security stance

Uniqueness lives in the **Keynest Seal** envelope and APIs, not in a custom cipher. Vault secrets use authenticated encryption (AES-256-GCM); master passwords use Argon2id hash-and-compare; MFA is a separate step. Per-character hashing is rejected — see [ADR-0001](./docs/ADR-0001-crypto-model.md) and [ADR-0003](./docs/ADR-0003-keynest-seal.md).

## Target stack

- **Libs + API:** Java 21, Spring Boot 3, Maven multi-module  
- **Manager UI:** Angular  
- **DB:** PostgreSQL  
- **Current tree:** legacy Next.js prototype (to be archived once Spring/Angular land)

## Docs

- [Product grooming](./docs/PRODUCT.md)
- [ADR-0001 — Crypto model](./docs/ADR-0001-crypto-model.md)
- [ADR-0002 — Platform & stack](./docs/ADR-0002-platform-and-stack.md)
- [ADR-0003 — Keynest Seal](./docs/ADR-0003-keynest-seal.md)

## Repo rename

Target GitHub name: **`keynest`**. Rename under GitHub → Settings → General.

## License / ownership

Private portfolio / product work by Jordy Nicholas.
