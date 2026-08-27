# Project ↔ PR operating model

Keynest uses **GitHub Issues + Projects** as the source of truth for delivery
(same board model as **TokenForge** and **Flux**). Issue bodies follow the
**Lume** pattern (Description / Approach / Definition of done) — see
[reference/board.md](./reference/board.md).

Branch names help humans and automation, but the **real link** is always an
issue reference on the pull request.

## Branch naming

```text
KN#<issue-number>
KN#<issue-number>-short-slug
```

Examples: `KN#12`, `KN#18-seal-rewrap`.

`KEYNEST#<n>` is also accepted by automation (case-insensitive).

The workflow `.github/workflows/project-pr-lifecycle.yml` parses this pattern and
appends `Closes #<n>` to the PR body when it is missing.

## Pull requests

Every task PR should:

1. Use a `KN#<n>` branch name when possible.
2. Include `Closes #<n>` (or `Fixes` / `Resolves`) in the PR body — this
   **auto-closes the issue when the PR merges**.
3. Prefer opening as **Ready for review** when you want peer review; use
   **Draft** while still implementing.
4. Map to **one existing Issue** (do not open orphan PRs).
5. Be **green on CI** before merge.

## Board Status automation

Columns: **Todo → In Progress → Ready for Review → Done** (+ **Epics Finished** for epics).

| Event | Board Status |
| --- | --- |
| Draft PR opened / converted to draft | In Progress |
| PR opened ready for review / marked ready | Ready for Review |
| Commits pushed to a non-draft PR | Ready for Review |
| PR merged / linked **story** issue closed | Done |
| Last child story of an **`[Epic]`** closed | Epic auto-closes → **Epics Finished** |
| **`[Epic]` issue closed** | **Epics Finished** |
| Child story of a closed **`[Epic]`** reopened | Epic reopens → Todo |
| **`[Epic]` issue reopened** | Todo |
| PR closed without merge | Todo |

Epics are detected by title prefix `[Epic]`. Story/task issues use **Done**.
The epic Action re-asserts **Epics Finished** after a short delay so the
built-in “closed → Done” project workflow does not win the race.

Also enable at the repository/project level:

- **Automatically delete head branches** after merge
- Built-in Project workflows: item closed → Done, PR merged → Done,
  PR linked to issue, item added → Todo
  (epics are corrected to **Epics Finished** by the Action above)

## Secret required

Repository secret `PROJECT_TOKEN` must be a PAT (or GitHub App token) that can
update the user Project board. Recommended fine-grained scopes:
**Contents: read**, **Issues: write**, **Pull requests: write**, **Projects: write**.

Without it, issue linking still works with `GITHUB_TOKEN`; board Status sync is
skipped (workflows emit a warning).

Create / refresh project IDs with:

```bash
PROJECT_TOKEN=… ./scripts/setup-github-project.sh --link-issues
```

Then paste the printed `PROJECT_*` / `STATUS_*` values into
`.github/workflows/project-pr-lifecycle.yml` and
`project-epic-lifecycle.yml`.

Workflow files: `.github/workflows/project-pr-lifecycle.yml`,
`project-epic-lifecycle.yml`.
