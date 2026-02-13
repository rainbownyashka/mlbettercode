# TODO (MLBetterCode Mod)

Format: `id | task | priority | owner | needs_user_test | state | links`

- MOD-001 | Reconcile `legacy/pages-work` fixes into `main` via safe cherry-picks | P0 | agent | yes | open | branches:main,legacy/pages-work
- MOD-002 | Finalize robust chest paging logs (signal/no spam balance) | P1 | agent | yes | in_progress | src/main/java/com/example/examplemod/feature/regallactions
- MOD-003 | Implement mirror switch config (default vercel, optional duckdns mirror) | P1 | agent | yes | open | docs/AGENT_WORK_PLAN.md
- MOD-004 | Add donor tier output in compile/plan print summary | P1 | agent | yes | open | donaterequire.txt
- MOD-005 | Keep Fabric 1.20/1.21 structure aligned with legacy module boundaries | P2 | agent | no | open | modern/fabric120, modern/fabric121
- MOD-006 | Add docs scope CI guardrails and keep rules green | P1 | agent | no | done | tools/check_docs_scope.py
- MOD-007 | Complete modern runtime-core parity for publish/parser/printer across 1.16.5/1.20/1.21 (run+confirm+publish bundle wired; full parity pending) | P0 | agent | yes | in_progress | modern/core, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-008 | Wire real Forge 1.16.5 runtime adapter (commands/events/containers) on top of core | P0 | agent | yes | open | modern/forge1165
- MOD-009 | Add adapter contract tests (GameBridge invariants + scoreboard parsing + error codes) | P1 | agent | no | open | modern/core
- MOD-010 | Add packet-skip and slot-desync retry verification in modern smoke tests | P1 | agent | yes | open | modern/*
- MOD-011 | Modern confirmload calls `placeadvanced` but command is not implemented in modern adapters; wire real place pipeline | P0 | agent | yes | in_progress | modern/core, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-012 | RuntimeCore confirmload maps command execution failures as plan parse failures; split by error code | P1 | agent | no | done | modern/core/src/main/java/com/rainbow_universe/bettercode/core/RuntimeCore.java
- MOD-013 | `/mldsl run <postId> <config>` accepts config but does not influence loading behavior; implement config-aware fetch | P1 | agent | yes | done | modern/core/src/main/java/com/rainbow_universe/bettercode/core/RuntimeCore.java
- MOD-014 | Fabric executeClientCommand path currently depends on network send methods; switch to local Brigadier dispatcher execution | P0 | agent | yes | done | modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-015 | Add `/modsettings` command with clickable controls and persisted JSON config for modern Fabric targets | P1 | agent | yes | done | modern/core/settings, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-016 | Fix Fabric 1.16.5 dependency id in `fabric.mod.json` (`fabric` vs `fabric-api`) for loader compatibility | P0 | agent | yes | done | modern/fabric1165/src/main/resources/fabric.mod.json
- MOD-017 | Add `/mldsl run local <path>` and `/mldsl check local <path>` with typed place-op execution bridge | P0 | agent | yes | done | modern/core, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-018 | Support `/mldsl run <postId> --config <id>` syntax and explicit local->server command dispatch logs | P0 | agent | yes | done | modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-019 | Replace local dispatcher dependency with explicit Java command-packet bridge + payload sanitization for place execution | P0 | agent | yes | done | modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-020 | Port true local placeadvanced runtime (no server command reliance): queue/tick/gui parity with legacy | P0 | agent | yes | open | modern/core, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-029 | Implement direct client-side block/action step executor in modern adapters (replace temporary `UNIMPLEMENTED_DIRECT_PLACE_RUNTIME`) | P0 | agent | yes | open | modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-030 | Port legacy menu/sign/args pipeline for direct runtime (`name/args` handling, GUI page routing, item(...) injection) | P0 | agent | yes | open | modern/core, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-031 | Verify legacy 1.12.2 `/module publish` forced nocache warmup no longer stalls on paged chest (`next_page_retry_exhausted` close path) | P0 | agent | yes | in_progress | src/main/java/com/example/examplemod/ExampleMod.java
- MOD-032 | Persist legacy 1.12.2 sign cache by `dim:pos` and use it in publish/export cache-mode fallback when live sign tile is missing | P1 | agent | yes | done | src/main/java/com/example/examplemod/ExampleMod.java, src/main/java/com/example/examplemod/io/CodeCacheIO.java
- MOD-033 | Enforce explicit export/publish error when sign block is present but no live/cache sign text exists (tell user to fly through code for sign cache) | P1 | agent | yes | done | src/main/java/com/example/examplemod/ExampleMod.java
- MOD-034 | Validate legacy sign cache entries (drop all-empty lines, do not save invalid signs, fail publish on empty-or-uncached sign data) | P1 | agent | yes | done | src/main/java/com/example/examplemod/ExampleMod.java
- MOD-021 | Port legacy place args/data layer into modern core and wire parser diagnostics before GUI runtime migration | P0 | agent | no | done | modern/core/place, modern/core/RuntimeCore
- MOD-022 | Port legacy item(...) spec parsing to platform-neutral core model and prepare adapter conversion path | P0 | agent | no | done | modern/core/place/ItemSpec, modern/core/place/PlaceArgsParser
- MOD-023 | Introduce typed place entry plan builder in modern core (pre-step for PlaceState/tick GUI runtime port) | P0 | agent | no | done | modern/core/place/PlaceEntrySpec, modern/core/place/PlacePlanBuilder, modern/core/RuntimeCore
- MOD-024 | Switch confirmload to queued tick executor (step-by-step runtime) and wire Fabric end-tick hooks | P0 | agent | yes | done | modern/core/RuntimeCore, modern/core/settings, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-025 | Extract queue state model (`PlaceRuntimeState`) from ad-hoc pending execution index to prep full PlaceState port | P0 | agent | no | done | modern/core/place/PlaceRuntimeEntry, modern/core/place/PlaceRuntimeState, modern/core/RuntimeCore
- MOD-026 | Introduce step-level place bridge API and route runtime tick executor through it (prep for non-command direct GUI executor) | P0 | agent | no | done | modern/core/GameBridge, modern/core/RuntimeCore, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-027 | De-duplicate Fabric command bridge code into shared core helper before GUI executor port | P1 | agent | no | done | modern/core/place/PlaceCommandBridgeUtil, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-028 | Preserve `skip` as typed runtime operation (not command string) in modern core/executor path | P1 | agent | no | done | modern/core/PlaceOp, modern/core/place/PlaceEntrySpec, modern/core/place/PlacePlanBuilder, modern/core/RuntimeCore
