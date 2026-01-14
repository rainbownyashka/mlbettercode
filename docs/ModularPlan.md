# Modularization Plan (ExampleMod.java)

## Goals
- Reduce the 11k-line `ExampleMod.java` into focused modules.
- Keep behavior identical during refactor.
- Always pass `gradlew build` after each move.

## Target Modules
- `api/` HTTP server, endpoints, JSON builders.
- `gui/` editor UI, input widgets, custom menus.
- `cache/` menu cache, click cache, chest cache, glass cache, shulker holo cache.
- `render/` holograms, previews, FBO/2D overlay.
- `logic/` coding mode detection, scoreboard parsing, glass/sign parsing.
- `cmd/` client commands (`/place`, `/exportline`, `/autocache`, `/test...`).
- `control/` hotbar, tp controls, keybinds.
- `io/` file read/write helpers, debounce saving, JSON/NBT helpers.
- `model/` small data classes (CachedMenu, ChestCache, ClickAction, PlaceEntry, etc.).

## Step-by-Step
1) **Inventory & grouping**
   - Map each method in `ExampleMod` to a module.
   - List shared data and cross-module dependencies.
2) **Define interfaces**
   - `CacheService`, `ApiService`, `GuiService`, `RenderService`, `CommandService`.
   - Central `ModContext` with references to `Minecraft`, config, and shared caches.
3) **Create skeleton packages**
   - Add empty classes with minimal stubs (no logic moved yet).
   - Keep `ExampleMod` as orchestrator and event router.
4) **Move low-risk utilities first**
   - Data models -> `model/`
   - File/NBT helpers -> `io/`
5) **Migrate subsystems incrementally**
   - `cache/` + persistence.
   - `api/` server.
   - `gui/` editor + input.
   - `render/` holograms and previews.
   - `cmd/` + `control/`.
6) **Cleanup**
   - Remove dead fields/methods from `ExampleMod`.
   - Keep only event wiring and module initialization.

## Transfer Rules (mandatory)
- Do not move event handlers or init wiring in the first phase.
- Do not rename fields, configs, commands, or keybinds during migration.
- Move only pure models/helpers first (no side effects).
- After each step: run `gradlew build`.
- No gameplay tests needed until final pass (one final test only).

## Strategy Notes
- Skip coremod for now. Modular migration first; coremod only if Forge API limits appear.
- One subsystem per PR; avoid giant rewrites.

## Final test
- One full build + one in-game smoke test at the very end.
