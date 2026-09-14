# ADR 0004 — Password policy JSON Schema (Zod-like contract)

## Status

Accepted

## Context

Generate and update must share one password standard. We want a declarative, versioned schema (similar in spirit to Zod) that:

1. Can be validated as a document (meta-schema).
2. Loads into a runtime `PasswordPolicy`.
3. Is reusable by Manager and external JVM services.
4. Is defined as a skeleton before vault CRUD is fully implemented.

## Decision

1. Authoritative meta-schema: `classpath:/schemas/password-policy.schema.json` (JSON Schema 2020-12).
2. Default instance: `classpath:/policies/keynest.default.v1.json`.
3. Load + validate with Jackson + `networknt/json-schema-validator` via `PasswordPolicyLoader`.
4. Resolve by id through `PolicyRegistry`; gate operations with `appliesTo` / `PolicyOperation`.
5. Application façade: `PasswordStandards` for create/update/rotate/generate validation hooks.
6. Runtime enforcement remains in `PasswordValidator` (and `keynest-generate` for creation).

## Consequences

- Policy docs are portable and reviewable as JSON.
- Invalid policy files fail at load time, not at first user request.
- Angular / non-JVM clients can read the same JSON Schema later (HTTP export or copied artifact).
- `bannedSubstrings` (case-insensitive) and `bannedRegexes` (Java `Pattern`, `Matcher.find`) are enforced by `PasswordValidator`. Invalid regexes fail at policy build/load.
- `PolicyExplainer.explain(policy)` produces UI copy aligned with validation rule codes.
- `maxAgeDays` remains a suggested rotation window; notify/rotate enforcement follows in Manager.
