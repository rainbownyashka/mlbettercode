# AGENT_WORK_PLAN

## Objective
Minimize user manual testing while preserving strict 1:1 runtime behavior with legacy 1.12.2.

## Operating Mode
- `parity-first`: behavior parity before refactors/cleanup.
- `evidence-first`: no "done" without artifacts (build, replay/contract checks, trace diff notes).
- `small-batches`: one risky behavior area per commit.
- `no-silent-fallback`: if fallback is unavoidable, log reason + branch/path explicitly.

## Architecture Direction
1. Keep all behavior logic in `modern/core` state machine.
2. Keep Fabric/Forge modules as thin adapters (`GameBridge` only).
3. Define per-version capabilities explicitly (not ad-hoc branching in core).
4. Reuse one shared test harness with fake bridge for deterministic checks.

## Quality Gates (Agent-owned)
For every non-trivial change, agent must provide:
1. Build result (at least target module or scripted build path).
2. Contract checks for touched behavior stage(s).
3. Trace evidence summary (state transitions/reason codes affected).
4. Explicit "out of scope / not changed" list.

If any gate fails, task remains `in_progress`; no "ready for user test" label.

## User Involvement Policy (Reduced Manual Load)
- User tests only at checkpoints:
  - checkpoint A: grouped runtime-flow fixes,
  - checkpoint B: grouped publish-flow fixes,
  - checkpoint C: release candidate.
- Between checkpoints, validation is agent-owned via harness + logs.

## Immediate Workstream
1. Close remaining parity gaps in printer/menu/args/postPlace with core-first patches.
2. Keep adapter drift low by moving duplicated behavior into core helpers only after parity evidence.
3. Stabilize performance by reducing hot-loop work without changing server-visible behavior.
4. Maintain trace clarity (`runtime_state`, `reason`, key ids) for each failure-prone stage.

## Definition of Done (per task)
1. Behavior contract for changed stage is satisfied.
2. No new silent fallback.
3. Docs updated (`STATUS.md`, `TODO.md`) with precise impact.
4. Commit includes reproducible verification notes.
