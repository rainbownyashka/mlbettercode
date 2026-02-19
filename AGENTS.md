# AGENTS (MLBetterCode Mod Repo)

## Repo role
- Project: `k:\mymod`
- Role: BetterCode mod codebase (Forge/Fabric), in-game runtime, print/export/load commands

## Forbidden scope
- Do not keep detailed compiler implementation docs here.
- Do not keep detailed site/backend/deploy docs here.

## External dependencies
- External dependency: `C:\Users\ASUS\Documents\mlctmodified` / `docs/AGENT_RUNBOOK.md`
- External dependency: `C:\Users\ASUS\Documents\mldsl-hub` / `docs/AGENT_RUNBOOK.md`

## Required workflow
1. Read `docs/STATUS.md` and `docs/TODO.md` before changing code/docs.
2. After meaningful changes, update `docs/STATUS.md` and `docs/TODO.md`.
3. For cross-project changes, update `docs/CROSS_PROJECT_INDEX.md`.

## Debug/Fallback policy
1. No silent fallback behavior by default.
2. Use fallback only when user explicitly asks for it, or when unavoidable for safety/runtime.
3. If fallback is used, log it transparently with reason and active branch/path so bug fixing remains traceable.

## Observability rule for large features
1. For any large/new feature, add minimal diagnostic logs in the most failure-prone places (state transitions, external I/O boundaries, mode switches, parser decisions).
2. Logs must be concise and structured (`FEATURE_STAGE`, key ids/flags/result), not noisy.
3. Logs must explain *why* a fallback/branch was chosen (if any).
4. Remove or downgrade temporary verbose logs after stabilization, but keep essential trace logs for support/debug.
