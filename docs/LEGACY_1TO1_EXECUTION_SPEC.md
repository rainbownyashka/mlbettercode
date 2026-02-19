# LEGACY 1:1 Execution Spec (Run + Publish)

## Goal
Freeze exact behavioral contract from legacy `1.12.2` and use it as a hard gate for modern runtime.
This file is a checklist of invariants, not a plan.

## Source Of Truth
- `src/main/java/com/example/examplemod/feature/place/PlaceTickHandler.java`
- `src/main/java/com/example/examplemod/feature/place/PlaceGuiHandler.java`
- `src/main/java/com/example/examplemod/feature/place/PlaceModule.java`
- `src/main/java/com/example/examplemod/ExampleMod.java`

## Run Contract (`/mldsl run`)

### R-01 Placement plane and click primitive
- Legacy behavior:
  - runtime `entry` is code block coordinate (block above light-blue glass row),
  - place target is `entry.down()`,
  - click primitive is `look packet -> use-item-on-block packet -> controller right-click` (`runPlaceClick`).
- Evidence:
  - `PlaceTickHandler.runPlaceClick(...)`
  - `PlaceTickHandler` place block stage uses `entry.pos.down()`.
- Modern status:
  - `PARTIAL`
  - place target is aligned to `entry.down`,
  - look spoof packet was missing before; now patched in adapters via reflective `sendLookPacketReflect`.
- Must verify in logs:
  - no dependency on camera direction for successful place/open,
  - no false `PLACE_CONFIRM_TIMEOUT` on step0 valid scenario.

### R-02 Menu-open target order
- Legacy behavior:
  - open menu by right-clicking sign at `entry.z-1` with Y scan `dy=-2..0`,
  - fallback to entry block click if sign not found.
- Evidence:
  - `findSignAtZMinus1(...)` in legacy,
  - menu open branch in `PlaceTickHandler`.
- Modern status:
  - `DONE_IN_CODE`
  - `openMenuAtEntryAnchor()` uses sign-first (`dy=-2..0`) then entry fallback.
- Must verify in logs:
  - `runtime_state=OPEN_MENU ...`
  - no endless `WAIT_MENU_ACK` on valid target.

### R-03 Menu ack and retry policy
- Legacy behavior:
  - wait for GUI progress,
  - if GUI not opened after retry window (`~1600ms`) do bounded retries,
  - after retry exhaustion trigger re-place path; no infinite loop.
- Evidence:
  - retry section in `PlaceTickHandler` and route handling in `PlaceGuiHandler`.
- Modern status:
  - `PARTIAL`
  - bounded retries + replace cycles are present,
  - still seeing `WAIT_MENU_ACK` loops in latest user log.
- Must verify in logs:
  - no repeated `WAIT_MENU_ACK` without eventual window progress or deterministic fail.

### R-04 Route resolution for event names
- Legacy behavior:
  - direct exact match, contains fallback, click-menu map fallback, scope routing, bounded random.
- Evidence:
  - `PlaceGuiHandler.findMenuStep(...)` chain and scope-specific branches.
- Modern status:
  - `PARTIAL`
  - exact/contains + alias + token/nbt fallback present,
  - legacy click-menu map fallback parity is still incomplete.
- Must verify in logs:
  - no `NO_PATH_GUI` for known aliases (`Событие входа` / `вход игрока`) on matching GUI.

### R-05 Params chest open target
- Legacy behavior:
  - after action click, params chest open target is `sign + (0,1,1)`,
  - fallback to `entry.up()` only when sign path is unavailable,
  - bounded retries + explicit timeout.
- Evidence:
  - `PlaceTickHandler` params branch.
- Modern status:
  - `DONE_IN_CODE`
  - sign-offset path and bounded timeout are present.

## Publish Contract (`/module publish`)

### P-01 Selected-row interpretation
- Legacy behavior:
  - selected anchor is light-blue glass row,
  - runtime entry is `glass.up`,
  - sign target is from entry: `entry + (0,dy,-1)`, `dy=-2..0`.
- Evidence:
  - legacy publish/export path uses `findSignAtZMinus1(world, entry)`.
- Modern status:
  - `DONE_IN_CODE`
  - `PublishRowContext`: `glass -> entry(y+1) -> sign(z-1)`,
  - `PublishSignResolver` scans sign Y offsets.

### P-02 Sign text validity and cache fallback
- Legacy behavior:
  - live sign text first,
  - fallback cache by `scope` then `dim:pos`,
  - empty sign lines are invalid; empty reads must not overwrite valid cache.
- Evidence:
  - `resolveCachedSignLines(...)`, `getLiveSignLines(...)`, publish sign invalid branches in `ExampleMod`.
- Modern status:
  - `PARTIAL`
  - cache hierarchy/persistence exists,
  - adapter sign-line reflection produced false-empty cases.
- Current patch:
  - added obf-safe field/method fallback in sign line readers for 1165/120/121.
- Must verify in logs:
  - valid sign no longer fails with `reason=sign_empty`,
  - `publish.sign.cache_hit source=<live|scope|dim>`.

### P-03 Scoreboard-scope cache key
- Legacy behavior:
  - cache scope is derived from scoreboard ID line, not coords-only key.
- Evidence:
  - `getCodeGlassScopeKey(...)` usage in legacy cache keys.
- Modern status:
  - `DONE_IN_CODE`
  - scope resolved from scoreboard and cached for late scoreboard load.

### P-04 Warmup/page-turn determinism
- Legacy behavior:
  - warmup queue with explicit blocked reasons,
  - page-turn bounded retries, explicit close on exhaustion.
- Evidence:
  - warmup and autocache sections in `ExampleMod`.
- Modern status:
  - `PARTIAL`
  - state machine and fail codes exist,
  - semantic parity still requires smoke validation on real server.

## Latest Known Blockers (from `latest.log`)
- `WAIT_MENU_ACK` repeats after accepted menu-open clicks.
- `PLACE_CONFIRM_TIMEOUT` appears after menu replace cycle.
- publish/sign path must be re-checked after sign-read patch.

## Hard "Done" Gate
- All three targets compile.
- Real-server smoke passes:
  - `/mldsl run` step0 `place -> open menu -> route`.
  - `/module publish` valid sign row without false sign-invalid.
- Log signatures absent:
  - endless `WAIT_MENU_ACK`,
  - false `PLACE_CONFIRM_TIMEOUT` on valid step0,
  - false `publish.sign.invalid reason=sign_empty` on non-empty sign.
