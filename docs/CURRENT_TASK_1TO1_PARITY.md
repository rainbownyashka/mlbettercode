# CURRENT TASK: 1:1 Parity Closure (`/mldsl run` + `/module publish`)

## Purpose
Закрыть текущие runtime регрессии до фактического 1:1 observable behavior относительно legacy 1.12.2.

## Communication Rule
- Статус "завершено" объявляется только после выполнения всех acceptance gates ниже.
- До этого любые сообщения считаются промежуточными ("in progress").

## Source Of Truth
- Legacy 1.12.2 implementation:
  - `src/main/java/com/example/examplemod/feature/place/PlaceTickHandler.java`
  - `src/main/java/com/example/examplemod/feature/place/PlaceGuiHandler.java`
  - `src/main/java/com/example/examplemod/feature/place/PlaceModule.java`
  - `src/main/java/com/example/examplemod/ExampleMod.java` (publish/warmup/sign-cache semantics)

## Active Evidence (latest.log, 2026-02-19)
1. `publish` still fails sign validation:
   - `stage=publish.row.resolve glass=219,70,139 entry=219,71,139 sign=219,71,138`
   - then `stage=publish.sign.invalid ... reason=sign_missing`
2. `run` still loops in menu ack then times out:
   - many `WAIT_MENU_ACK`
   - `MENU_REPLACE` -> `FORCE_REPLACE`
   - `PLACE_CONFIRM_TIMEOUT ... expected=minecraft:diamond_block`
3. `run` seed is still look-driven:
   - `direct_runtime_start seed=... z=148`
   - next run `seed=... z=147`
   - same selected row context, meaning crosshair fallback still influences runtime start.

## Current Task Breakdown (must all be done)

### A. Run Seed/Placement Parity (critical)
- Remove crosshair-driven seed usage from critical `/mldsl run` path when valid selected rows exist.
- Select nearest valid row (block above `light_blue_stained_glass`) in current dimension.
- Confirm placement plane parity:
  - `entry = glass.up`
  - place target semantics aligned with legacy step contract.
- Explicit fail when no valid row context (no silent fallback).

### B. Publish Sign Contract Parity (critical)
- Keep row normalization `glass -> entry(y+1) -> sign(z-1)` as mandatory.
- Read live sign lines from resolved sign target with strict validation.
- Cache fallback behavior:
  - allowed sources: `live|scope|dim`
  - empty live read must not be treated as valid sign.
- Error reasons must remain explicit:
  - `sign_missing`, `sign_empty`, `cache_miss`.

### C. Menu ACK / Place Confirm Stability (critical)
- Prevent false-positive open attempts that do not lead to actual GUI progress.
- Keep strict bounded retries.
- Do not trigger force-replace when menu is already confirmed/stable.
- Ensure place-confirm checks target the expected runtime block location and state.

### D. Legacy GUI Route Matching
- Preserve exact->contains ordering.
- Keep aliases for `вход игрока` / `событие входа`.
- Port remaining `clickMenuMap`-style fallback semantics where needed to eliminate residual `NO_PATH_GUI` mismatch.

## Progress (evidence-based)

### Implemented In Code (not final-accepted yet)
1. Run seed resolver strictness:
   - removed crosshair fallback from direct runtime seed resolution in:
     - `modern/fabric1165/.../BetterCodeFabric1165.java`
     - `modern/fabric120/.../BetterCodeFabric120.java`
     - `modern/fabric121/.../BetterCodeFabric121.java`
   - selected row is now interpreted as `glass`, and runtime `entry` is resolved as `glass.up` for nearest-row seed selection.

2. Menu open sign target legacy Y-offset parity:
   - adapter `openMenuAtEntryAnchor()` now tries sign target at `entry.z-1` with `dy=-2..0` before entry fallback in all three Fabric adapters.

3. Publish sign resolver legacy Y-offset parity:
   - `PublishSignResolver` now resolves sign position with legacy-equivalent scan (`entry.z-1`, `dy=-2..0`) before reading lines/cache resolution.

4. Publish trace/error coordinate clarity:
   - `RuntimeCore.validatePublishSigns(...)` now reports entry coordinates from normalized `PublishRowContext` in trace/error payloads.
5. Run placement plane parity:
   - direct placement target corrected to legacy-equivalent `entry.down` (blue-glass plane), not shifted target,
   - run seed remains strict (no crosshair fallback), and selected rows are treated as glass anchors.
6. Params open target parity:
   - core params-open now tries legacy-equivalent `sign+(0,1,1)` candidates using sign Y-offset scan (`dy=-2..0`) before fallback.
7. Sign block detection parity hardening:
   - adapter `isSignAt(...)` no longer relies only on block-entity class name; now also checks block registry id containing `sign` (obf-safe).
8. GUI route normalization parity hardening:
   - core `norm()` now strips MC formatting/punctuation and normalizes spaces (closer to legacy normalize-for-match semantics),
   - menu slot lookup now has additional fallback by tokenized NBT text, reducing false `NO_PATH_GUI` when display names differ.
9. Block recheck coordinate parity:
   - fixed core placed-block recheck to validate `entry.down` (legacy place plane) instead of `entry`,
   - removes false block-lost detection that could trigger unnecessary `FORCE_REPLACE`/timeout chains.
10. Publish cache key parity hardening:
   - modern `scopeKey` now includes dimension (`dim:row:x:y:z`) to avoid cross-dimension cache collisions for identical coordinates.
11. Persistent publish sign-cache (legacy parity slice):
   - added disk-backed cache file for modern publish: `mldsl_cache/publish_sign_cache.json`,
   - persisted payload now includes:
     - `scope` sign lines cache,
     - `dimPos` sign lines cache,
     - `entryToSign` mapping (`dim:entry:x:y:z` -> `dim:sign:x:y:z`),
   - cache is loaded at publish start and saved after warmup/sign-validation stages (including fail paths).

### Verified Now
1. Compile gates passed:
   - `modern/fabric1165: compileJava` (pass)
   - `modern/fabric120: compileJava` (pass)
   - `modern/fabric121: compileJava` (pass)

### Still Pending (required before "Done")
1. Runtime smoke + log gate re-check on real server for:
   - `/module publish` selected-row sign scenario
   - `/mldsl run` step0 place/menu scenario
2. Confirm absence of blocker signatures:
   - `publish.sign.invalid ... sign_missing` (for valid sign case)
   - endless `WAIT_MENU_ACK`
   - `PLACE_CONFIRM_TIMEOUT` on step0 valid path
   - look-driven seed drift in `direct_runtime_start`.
3. Publish cache persistence parity:
   - confirm runtime behavior on live server that persisted cache is reused between separate game runs,
   - confirm no stale/empty overwrite regressions vs legacy when sign temporarily unloads.

## Required Trace Gates (must be visible in logs)

### Run
- `runtime_state=PLACE_BLOCK confirmed=1`
- `runtime_state=OPEN_MENU ...`
- no endless `WAIT_MENU_ACK`
- no `PLACE_CONFIRM_TIMEOUT` for step0 on valid scenario
- no `NO_PATH_GUI` for `Событие входа` / `вход игрока`

### Publish
- `stage=publish.row.resolve glass=... entry=... sign=...`
- no `publish.sign.invalid ... sign_missing` when valid sign exists
- no false `warmup.done` on unresolved/blocked states

## Acceptance Definition (Done = all true)
1. Compile success on all targets:
   - `modern/fabric1165`
   - `modern/fabric120`
   - `modern/fabric121`
2. Server smoke pass:
   - `/module publish` selected-row scenario
   - `/mldsl run ...` with first step event menu path
3. Log gate pass:
   - none of known blocker signatures above present
4. Docs updated:
   - `docs/STATUS.md`
   - `docs/TODO.md`

## Non-Negotiable Constraints
- No silent fallback in critical runtime/publish paths.
- Legacy behavior priority over modern API convenience.
- "Done" can be stated only after evidence-based gate pass.
