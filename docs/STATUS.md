# STATUS (MLBetterCode Mod)

## Current release baseline
- Legacy mod jar baseline: `bettercode-1.0.9`
- Active branch baseline: `main` (mod repo)

## Verified features
- `/mldsl run` plan execution path wired to place pipeline.
- Chest page merge logic stores and reuses merged snapshot across pages.
- `/loadmodule` supports explicit error reasons (timeout/http/ssl/etc.).
- Donor tier resolver from loaded file IDs available in hub module flow.
- Modern runtime core scaffold added under `modern/core`:
  - shared `RuntimeCore`, `GameBridge`, `RuntimeErrorCode`, `SignLineNormalizer`.
  - `ScoreboardParser` + `ScoreboardContext` for explicit scoreboard-derived context logging.
  - explicit debug tags: `publish-debug`, `printer-debug`, `confirmload-debug`.
  - `/mldsl run` now performs real Hub download into `mldsl_modules/<postId>`.
  - `/loadmodule` alias added in modern Fabric targets (`/loadmodule <postId> [file]`).
  - `/confirmload` now parses downloaded `plan.json` and dispatches `/placeadvanced ...` commands through adapter command bridge.
  - `/module publish` now prepares local bundle (`mldsl_publish/bundle_*`) with copied files and metadata JSON.
  - `/modsettings` added in modern Fabric targets: clickable chat controls for bool/int/string settings with persisted JSON config.
  - `/mldsl run <postId> [config]` now performs config-aware fetch (`config` is propagated to Hub `/files` and `/file`).
  - printer diagnostics improved: failed command execution now logs exact `placeadvanced` command + step index (`printer-debug`) in core and Fabric adapters.
  - command execution failures now propagate full adapter stacktrace in error payload/logs (no silent exception swallow).
  - modern runtime now supports local plan staging/check commands:
    - `/mldsl run local <path>`
    - `/mldsl check local <path>`
  - core confirmload now executes typed place operations through `GameBridge.executePlacePlan(...)` (no direct command-string execution inside core).
  - `/mldsl run` now accepts config in two forms:
    - positional: `/mldsl run <postId> <config>`
    - flag: `/mldsl run <postId> --config <config>`
  - modern place execution now sends explicit command packets via Java bridge (`sendChatCommand`/`sendCommand`/`sendChatMessage` by API availability), not local dispatcher.
  - place command payload is sanitized before send (strip color codes/control chars, normalize block id) to reduce invalid-symbol kicks.
  - explicit logs added per step:
    - `[printer-debug] server_command_sent ...`
    - `[printer-debug] server_command_failed ...`
  - place args parser core port started in modern runtime:
    - new `modern/core/place/PlaceArgsParser` + `PlaceArgSpec` + `PlaceInputMode`.
    - `confirmload/check` now logs `place_args_summary` from parsed operation args.
  - item-spec parsing (`item(...)`) moved to modern core parser:
    - new `modern/core/place/ItemSpec`.
    - `PlaceArgSpec` now carries parsed `itemSpec`.
    - diagnostics extended with `itemSpecs=` count.
  - typed plan layer added for direct runtime migration:
    - new `modern/core/place/PlaceEntrySpec` and `PlacePlanBuilder` (from `List<PlaceOp>`).
    - `RuntimeCore` summaries now operate on typed entries (`entries`, `pauses`, parsed args).
- Modern targets now include bootstrap modules:
  - `modern/fabric1165` (new),
  - `modern/fabric120`,
  - `modern/fabric121`,
  - `modern/forge1165` (bootstrap jar, Forge hooks pending).
- Fabric 1.16.5 loader compatibility fix applied:
  - `fabric.mod.json` dependency switched from `fabric-api` to `fabric` for TL/Fabric Loader resolution.

## Known regressions / risks
- Ongoing high-risk area: GUI/chest timing races on unstable server latency.
- Cross-branch divergence (`legacy/pages-work` vs `main`) not fully reconciled.
- Mixed working tree artifacts may appear during local experiments.
- Modern publish/parser/printer parity with legacy 1.12.2 is still incomplete.
- `forge1165` currently ships bootstrap-only artifact (no runtime Forge event wiring yet).

## Last user-tested
- Last manually confirmed by user: `resave2` multi-page save behavior marked as working in project notes.
- Re-verify after any chest paging code change.

## Notes
- This file is mod-only SoT. Do not put compiler/site operational details here.
