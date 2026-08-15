# Keynest

Self-hostable password vault with an optional cloud deploy path.

Store login credentials, generate strong passwords, get notified before they expire, and unlock access with a master password plus optional MFA (email / SMS / TOTP).

> Formerly `fep-password` — a Next.js password-generator experiment. This repo is being reshaped into a real vault product. See [docs/PRODUCT.md](./docs/PRODUCT.md) for grooming, naming, and the security model.

## What it will do

| Capability | Status |
|---|---|
| Password generator (CSPRNG) | Prototype UI exists — needs crypto fix |
| Credential vault (URL, username, password) | Stub |
| Password expiry + notify / rotate | Planned |
| Master-password auth (Argon2id) | Planned (`bcrypt` / `next-auth` present) |
| MFA after unlock (email / SMS / TOTP) | Planned |
| Docker self-host + cloud deploy | Planned |

## Security stance (read this)

Vault secrets are **encrypted** (AES-256-GCM) under a key derived from your master password.  
The master password itself is stored only as an **Argon2id** hash of the *whole* string.

We will **not** hash passwords character-by-character or “reverse engineer” hashes. That approach is trivially breakable and is not how password managers work. Details and the corrected flow live in [docs/PRODUCT.md](./docs/PRODUCT.md).

## Stack (current)

- Next.js 14, React 18, TypeScript
- `bcrypt` / `next-auth` already in dependencies (to be evaluated vs Argon2id + session design)

## Local dev

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Repo rename

Target GitHub name: **`keynest`**. Rename under GitHub → Settings → General. This package is already named `keynest`.

## License / ownership

Private portfolio / product work by Jordy Nicholas.
