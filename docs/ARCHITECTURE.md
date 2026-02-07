# Architecture

## Purpose
`bettercode` is a 1.12.2 client mod that exports K+ visual code rows into deterministic JSON and orchestrates conversion/publish/print workflows with MLDSL.

## Runtime layers
- `feature/export`
- `ExportScanCore`: discover candidate blue-glass rows.
- `ExportCodeCore`: strict row serialization (`entry=-2x`, `side=-1x`, `noFallback`).
- `ExportArgDecodeCore`: decode chest slots/enums/typed args.
- `feature/place`
- `PlaceParser` + `PlaceModule`: turn plan JSON into in-game print actions.
- `feature/mldsl`
- `MlDslModule`: invoke local `mldsl.exe` for exportcode->mldsl->plan flows.
- `feature/hub`
- publish/download integration paths.

## Non-negotiable contracts
- Row geometry: entry step `-2x`, side check `-1x` only.
- Side piston semantics: `west` => open brace, `east` => close brace.
- No silent fallback parser branches.
- Debug logs must print active parser logic marker at row start.

## Data contracts
- Export JSON `version=2`.
- Chest slots are absolute indices across pages (0-based).
- Downstream compiler/printer must accept `slot >= chest_size`.

## Quality gates
- CI must pass: `gradlew test build` + `runExportSim` smoke.
