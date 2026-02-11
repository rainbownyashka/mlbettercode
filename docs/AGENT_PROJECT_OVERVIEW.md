# AGENT_PROJECT_OVERVIEW

## Purpose
MLBetterCode (BetterCode) is a client-side Forge mod for Minecraft 1.12.2 focused on:
- faster code-print workflows on Mineland Creative+/K+;
- GUI/chest-driven action automation;
- import/export utilities for code schemes and MLDSL plans.

## What Is Implemented (High Level)
- Print pipeline for blocks/signs/actions (`/placeadvanced`, `/mldsl run`).
- Module load/confirm flow from MLDSL Hub (`/loadmodule`, `/confirmload`).
- Export tools (`/exportcode`, selector/floor helpers).
- Code copy tools (`/copycode`, `/cancelcopy`).
- GUI action automation and resave/export helper (`/regallactions` family).
- Chest page merge/snapshot logic with rich trace logs (`CHEST_PAGE_*`, `RGA_*`, `PUBLISH_TRACE`).

## Build Targets (Current)
- `legacy112` (Forge 1.12.2): main/full BetterCode implementation.
- `fabric120` (Fabric 1.20.1): modern bootstrap branch.
- `fabric121` (Fabric 1.21.1): modern bootstrap branch.

Build matrix entrypoint:
- `tools/build_matrix.py`

Java requirements by target:
- `legacy112` -> JDK 8
- `fabric120` -> JDK 17
- `fabric121` -> JDK 21

## How It Works (Conceptual)
- Commands trigger world interaction steps (click/open/read GUI/chest state).
- GUI/chest snapshots are transformed into normalized export records.
- Multi-page chest content is merged before final resave/export use.
- MLDSL plan (`plan.json`) can be executed in-game via command flow.

## Build
Build instructions already exist in:
- `docs/MULTI_VERSION_BUILD.md`
- `docs/QUICKSTART_RU.md`
- `README.md`

This file intentionally does not duplicate full build steps.

## Testing Model
- Primary validation is real gameplay behavior + user test feedback.
- Logs are critical source of truth (`latest.log`, `CHEST_PAGE_*`, `RGA_*`).
- Repro and outcomes must be tracked in `docs/AGENT_WORK_PLAN.md`.

## Current Known Focus Area
- Reliability of `regallactions resave2` multi-page GUI/chest merge behavior.
- Eliminate accidental interference from unrelated click/state flows.
- Keep logic close to vanilla live GUI behavior (no hidden stale cache effects).

## Related Agent Docs
- `AGENTS.md`
- `docs/AGENT_WORK_PLAN.md`
- `docs/AGENT_TAG_INDEX.md`
- `docs/AGENT_TOOL_REGISTRY.md`
