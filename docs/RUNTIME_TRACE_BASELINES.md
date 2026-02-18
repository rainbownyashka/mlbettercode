# Runtime Trace Baselines (Legacy 1.12.2 -> Modern)

Purpose: lock behavior-critical trace scenarios before and during multi-version porting.

## Scenario set

1. `cursor_not_empty`
- Input: `placeadvanced item(...)` flow with forced occupied cursor.
- Expected:
  - waits up to 5s for cursor clear
  - explicit abort with `cursor_not_empty`
  - no silent cursor cleanup branch
- Required traces:
  - `PLACE_ITEM_TRACE step=begin`
  - `PLACE_ITEM_TRACE step=after_click` (optional if aborted early)
  - abort reason in printer/runtime logs

2. `item_nbt_name_multi_chest`
- Input: 2-3 sequential entries with `item(...)` (display name + NBT payload).
- Expected:
  - stable target insertion in all steps
  - temp hotbar slot restore after each step
  - no name/NBT loss between first and last chest
- Required traces:
  - `PLACE_ITEM_TRACE step=begin`
  - `PLACE_ITEM_TRACE step=after_click`
  - `PLACE_ITEM_TRACE step=restore`

3. `menu_page_turn`
- Input: menu args requiring page navigation.
- Expected:
  - deterministic page movement
  - bounded retry behavior
  - explicit abort on retry exhaustion
- Required traces:
  - `printer-debug` step/branch logs
  - page turn retry reason/error

4. `publish_warmup_nocache`
- Input: `/module publish ... nocache` on paged chest set.
- Expected:
  - deterministic TP path
  - no infinite wait on page transitions
  - explicit stop/close on timeout or retry exhaustion
- Required traces:
  - `PUBLISH_TRACE stage=warmup.*`
  - `PUBLISH_TRACE stage=autocache.close reason=next_page_retry_exhausted` (if triggered)

5. `publish_warmup_cache`
- Input: cache mode publish with partial stale/missing chest snapshots.
- Expected:
  - warm only missing/refresh-needed chests
  - stable settle window
  - continue to export/compile only after warmup completion
- Required traces:
  - `PUBLISH_TRACE stage=publish.cache.warmup.start`
  - `PUBLISH_TRACE stage=warmup.done`

6. `sign_cache_hit_miss_invalid`
- Input: mixed rows (live sign, cache hit, missing/invalid sign).
- Expected:
  - cache hit path logs source/key
  - invalid/missing sign is explicit hard fail
- Required traces:
  - `PUBLISH_TRACE stage=publish.sign.cache_hit`
  - `PUBLISH_TRACE stage=publish.sign.invalid`

## Current modern checkpoint

- Direct `run` core state machine is active with explicit fail/trace markers (`MENU_OPEN_REPLACE_EXHAUSTED`, `BLOCK_REVERTED_TOO_MANY_TIMES`, `PARAMS_TARGET_NOT_FOUND`).
- Modern publish now emits core-owned `PUBLISH_TRACE` stages and accepts both pending-load payload and live selector rows.
- Modern publish warmup now has explicit blocked-reason stages (`dimension_mismatch`, `not_dev_creative`, `blocked`, `tp_path_busy`), bounded page-turn retries, and deterministic exhaustion close trace (`autocache.close reason=next_page_retry_exhausted`).
- Modern publish now emits explicit sign-stage traces (`publish.sign.cache_hit`, `publish.sign.invalid`) and returns mapped runtime codes (`PUBLISH_WARMUP_TIMEOUT`, `PUBLISH_TP_UNAVAILABLE`, `PUBLISH_CONTEXT_BLOCKED`, `PUBLISH_NEXT_PAGE_RETRY_EXHAUSTED`, `PUBLISH_SIGN_INVALID`).
- Modern core now uses adapter-backed sign resolver (`isSignAt`/`readSignLinesAt`) with cache source mapping (`live|scope|dim`); remaining gap is live-server trace alignment for sign/page timing (`MOD-077`, `MOD-078`, `MOD-079`).
- Run block-revert handling now emits `runtime_state=BLOCK_RECHECK miss=<n> elapsed=<ms>` before replace/fail to separate transient desync from true revert.
