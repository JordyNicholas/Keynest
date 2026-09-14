# Coding standards (lightweight)

Companion to ADRs and [PRODUCT.md](./PRODUCT.md). Prefer EditorConfig over ad-hoc formatter debates.

## Indentation

- **2 spaces** everywhere (Java, TypeScript, JSON, YAML, XML, properties, etc.).
- No tabs except `Makefile`.
- Continuation / wrapped lines: also **2** (see root [`.editorconfig`](../.editorconfig)).
- Do not mass-reformat unrelated files in a feature PR; new and touched code follows this rule.

Root [`.editorconfig`](../.editorconfig) is authoritative. `apps/manager-web/.editorconfig` only adds TS quote prefs and must **not** set `root = true`.

## Java (Manager API + libs)

- Package layout and thin controllers stay as in PRODUCT / ROADMAP (validate → use case → libs).
- Match neighboring type style in the file you edit; prefer 2-space indent on new files going forward.
- Boolean properties: JavaBean `isX()` / `setX()` (e.g. `isEnabled()`).
- Config types: `@ConfigurationProperties` next to siblings under `dev.keynest.manager.config`.

## TypeScript / Angular

- Single quotes (EditorConfig `quote_type = single`).
- Node 20+; Angular major per [ADR-0005](./ADR-0005-java-and-toolchain-policy.md).

## Misc

- UTF-8, LF line endings, final newline, trim trailing whitespace (except Markdown trailing breaks).
- Do not commit secrets; keep demo keys out of docker/prod profiles (see Keynest #16).
