# Agent instructions

Resolve shared policy using `.team-policy/pointer.json` (paths relative to this repository). Clone the configured remote into the first candidate if missing; otherwise fetch and fast-forward the tracked branch when safe. Report blocked updates.

Before implementing, read the resolved checkout's `README.md` and follow its linked guidance relevant to the task.

When working with issues or PRDs, read `docs/agents/issue-tracker.md` and `docs/agents/triage-labels.md`.

Before exploring domain behavior, read `docs/agents/domain.md`.

When changing configured-world generation or its bootstrap lifecycle, read `docs/custom-worlds.md`.
