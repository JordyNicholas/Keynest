# Design documents

- [AI and agent architecture](ai-and-agents.md) describes the proposed trust boundaries and future capabilities. The implemented optional explanation has its own [runtime/API guide](../api/policy-explanations.md).
- Accepted M1 design contracts: [AI egress](../architecture/decisions/ADR-0019-ai-egress-boundaries.md), [agent trust](../architecture/decisions/ADR-0020-agent-trust-boundaries.md), and the [standards spike](ai-agent/STANDARDS_SPIKE.md). The maintainer approved the contracts in #327; the standards record is research, and later capabilities remain unimplemented and disabled.
- [Homepage and account overview](home-overview/DESIGN.md) records the reviewed design and links to its synthetic prototype. The [Manager guide](../manager/README.md) documents the current application.
- [Idea catalog](ideas/CATALOG.md) and [idea format](ideas/FORMAT.md) support product exploration. Release scope and sequencing are tracked in the [canonical roadmap](../contributing/project/roadmap.md).

- [Browser transport reassessment](browser-transport/ADR_0007_REASSESSMENT.md) proposes an opt-in same-origin cookie/CSRF migration; the [H1 evidence matrix](browser-transport/H1_HEADERS.md) separates delivered headers from remaining gates. ADR-0007 remains unchanged.
