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
  - `confirmload` execution switched to queued runtime mode in `RuntimeCore`:
    - plan is staged as `PendingExecution`,
    - steps are executed on client ticks with configurable delay (`printer.stepDelayMs`, default `80` ms),
    - tick logs: `tick step ok/failed`, completion actionbar.
  - Fabric adapters now register end-tick hook and drive `RuntimeCore.handleClientTick(...)`.
  - runtime queue state extracted into core place model:
    - new `PlaceRuntimeEntry` + `PlaceRuntimeState`,
    - `PendingExecution` now runs through `PlaceRuntimeState` instead of raw list index.
  - typed runtime step model now preserves `skip` as a dedicated move-only operation:
    - `PlaceOp.Kind.SKIP` added,
    - parser supports standalone `skip` tokens in `placeadvanced` arrays,
    - plan builder/runtime entry carry `skip` flag explicitly (no `placeadvanced skip ...` command synthesis).
  - bridge contract advanced toward direct runtime execution:
    - `GameBridge.executePlaceStep(PlaceRuntimeEntry, checkOnly)` added,
    - `RuntimeCore` tick executor now calls step-level bridge API,
    - Fabric adapters implement step executor and reuse it from batch path.
  - modern Fabric step executors no longer send `placeadvanced` to server chat:
    - server-command bridge removed from `executePlaceStep`,
    - block/action steps now fail explicitly with `UNIMPLEMENTED_DIRECT_PLACE_RUNTIME` until direct GUI/tick runtime is fully ported.
  - `PlaceExecResult` now supports `inProgress`; runtime tick loop can keep current step without auto-advancing.
  - direct client-side place executor baseline added in modern Fabric adapters:
    - runtime start/stop hooks reset adapter execution state (`seed`, `cursor`),
    - `skip` is handled locally by cursor advance,
    - block-only steps with empty `name/args` place by local `interactBlock` using selected/crosshair seed and hotbar item.
  - explicit unsupported branch (transparent, no fallback):
    - steps with non-empty `name/args` now fail as `UNIMPLEMENTED_MENU_ARGS` (GUI/menu arg pipeline not ported yet).
  - duplicated Fabric command bridge logic reduced:
    - common `PlaceCommandBridgeUtil` introduced in `modern/core/place`,
    - command build/sanitize/send reflection moved out of adapters.
- Modern targets now include bootstrap modules:
  - `modern/fabric1165` (new),
  - `modern/fabric120`,
  - `modern/fabric121`,
  - `modern/forge1165` (bootstrap jar, Forge hooks pending).
- Fabric 1.16.5 loader compatibility fix applied:
  - `fabric.mod.json` dependency switched from `fabric-api` to `fabric` for TL/Fabric Loader resolution.
- Legacy `1.12.2` no-cache publish warmup anti-stall fix:
  - page-turn retries now advance on `slot_not_found` and click exceptions,
  - warmup no longer hangs forever on `has_next_page`; after retry exhaustion it closes chest with explicit `publish_trace` reason `next_page_retry_exhausted`.
- Legacy `1.12.2` persistent sign cache improvements:
  - sign cache now persists both scope-key and `dim:pos` keys in `code_cache.dat`,
  - publish/export row parser (`preferChestCache=true`) now falls back to cached sign lines by `dim:pos` when live sign tile is unavailable.
  - strict publish/export fail added: if sign block exists but no live text and no cached text, export stops with explicit message:
    "Облети весь код, чтобы закэшировать таблички, и повтори /module publish."
  - sign cache now rejects invalid empty signs:
    - all-empty sign lines are not saved to cache,
    - existing cache entry for that sign is removed when sign becomes empty,
    - publish/export treats empty sign content as missing data and fails with explicit guidance to recache by flying through code.

## Migration checkpoint (where port currently stops)
- Direct runtime port is active in modern Fabric adapters only for:
  - `skip` steps,
  - block-only steps with empty `name/args`.
- Steps with menu/sign/action arguments (`name/args`) are still not ported to direct client executor and remain blocked by explicit `UNIMPLEMENTED_MENU_ARGS`.
- Next required porting block: legacy menu/sign args pipeline (`PlaceState` parity) into modern adapters/core (see TODO `MOD-030`).

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
