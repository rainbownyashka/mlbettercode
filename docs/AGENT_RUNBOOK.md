# Agent Runbook (MLBetterCode Mod SoT)

This runbook is the **source of truth for `k:\mymod` only**.

## Repo role
- Project: `k:\mymod`
- Role: Minecraft client mod (Forge 1.12.2 + modern Fabric targets)
- Scope: mod runtime, commands, print pipeline, parser/export runtime integration, release/build of mod jars

## Forbidden scope
Do not place detailed operational docs here for:
- compiler internals (`mlctmodified`)
- site/backend/deploy (`mldsl-hub`)

Use external dependency links instead.

## External dependencies
- Compiler: `C:\Users\ASUS\Documents\mlctmodified` / `docs/AGENT_RUNBOOK.md`
- Site/Hub: `C:\Users\ASUS\Documents\mldsl-hub` / `docs/AGENT_RUNBOOK.md`

## Build (legacy 1.12.2)
Run from `k:\mymod`:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon clean jar
```

Output:
- `k:\mymod\build\libs\bettercode-1.0.9.jar`

## Multi-version matrix
See `docs/MULTI_VERSION_BUILD.md`.

## Critical commands (in-game)
- `/mldsl run [path] [--start N]`
- `/placeadvanced ...`
- `/loadmodule <postId> [file]` + `/confirmload`
- `/exportcode [floorsCSV] [name]`
- `/codeselector`
- `/copycode ...` + `/cancelcopy`

## Regression anchors
- chest page traversal/merge behavior
- `/module publish` export determinism
- `/loadmodule` download + confirm flow
- encoding safety in RU literals

## Operational rule
Before any change, read `docs/STATUS.md` and `docs/TODO.md`.
After any meaningful change, update both.
