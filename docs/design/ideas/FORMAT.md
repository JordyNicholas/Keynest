# Idea record format

**Status:** proposed working format. It does not change the [roadmap](../../contributing/project/roadmap.md), the charter, or any accepted ADR.

Use this format for every product idea captured during brainstorming. One record per idea. Keep rejected ideas in the catalog so they are not proposed again under a new name.

## Identifiers

- `IDEA-NNN` for an open idea. Numbers are stable. Do not reuse a number.
- `IDEA-Rnn` for an idea the conversation has already set aside.

## Required fields

| Field | What to write |
| --- | --- |
| Status | `captured` (recorded, not designed), `refined` (the conversation changed the original shape and the record is the current shape), `rejected`, or `selected` (chosen for a design pass). |
| Interest | `unrated` until brainstorming is closed. Then `high`, `medium`, or `low`, with one sentence. |
| Sequence | Blank, or the agreed design order (`1`, `2`, `3`) when this idea is a design target. Supporting ideas name their parent instead of taking a number. |
| Parents | Other idea ids this one depends on or belongs to. `none` if it stands alone. |
| Origin | Who raised it and the date. Use America/Sao_Paulo dates. |
| Problem | The user or integrator difficulty, in one or two sentences. |
| Proposal | What would be built, as currently understood. |
| End-user decision | The choice only the end user can make. Name the control they confirm. |
| Visibility | Who can see the data and the model output. Include the end user, their own agent, an embedding site, and Keynest as an operator. |
| Model may see | The exact classes of input. Write `none` when the idea does not use a model. |
| Model must not see | Secrets, keys, and any other forbidden input. |
| Deterministic core | What still works with AI disabled, and which component decides. |
| Integration | Where it meets the user or the other system: vault UI, browser companion, embeddable library, themeable component, or opt-in app. |
| Distinctiveness | What a comparable tool does not do. Name the tool class when it matters. |
| Dependencies | Ideas, ADRs, or current code this needs. |
| Open questions | Unresolved choices. `none` when the record is settled enough to design. |

## Rejected-record fields

A rejected record keeps Status, Origin, Proposal, and adds **Why rejected** and **Superseded by**. Leave the model and visibility fields in when they explain the rejection.

## Standing constraints

New ideas inherit the constraints in the catalog. A record that breaks one says so in Open questions. Do not weaken a constraint inside a proposal.

## When brainstorming closes

1. Set Interest on every `captured` and `refined` idea.
2. Fill the evaluation map in the catalog: design now, keep as support, hold, or reject.
3. Only then write a design document for the sequence-1 idea. Sequence 2 and 3 wait for that design to exist.

Interest is not permission to build. Security-sensitive work still needs an ADR and explicit maintainer approval.
