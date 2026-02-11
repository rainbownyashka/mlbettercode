# CROSS_PROJECT_INDEX

## Project
- Name: MLBetterCode Mod
- Path: `k:\mymod`
- Role: Minecraft mod runtime and in-game automation
- Last verified: 2026-02-11

## Depends on
- `mlctmodified` (compiler): generated `plan.json` contract and export conversion behavior.
- `mldsl-hub` (site): module file API contract for `/loadmodule`.

## Inbound contracts
- Compiler outputs valid plan entries consumed by `/mldsl run`.
- Hub serves module files via stable endpoints.

## Outbound contracts
- Mod executes `plan.json` and prints deterministic code rows.
- Mod supports module loading/confirmation UX.

## External dependency links
- External dependency: `C:\Users\ASUS\Documents\mlctmodified` / `docs/AGENT_RUNBOOK.md`
- External dependency: `C:\Users\ASUS\Documents\mldsl-hub` / `docs/AGENT_RUNBOOK.md`
