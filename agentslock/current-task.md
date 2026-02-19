# Agent Lock

task_id: MOD-091+MOD-092+MOD-094+MOD-095+MOD-096+MOD-097+MOD-098+MOD-099+MOD-100+MOD-101+MOD-102+MOD-103+MOD-104+MOD-105+MOD-106+MOD-107
owner: codex
status: in_progress
scope: 1:1 parity migration; run/publish parity with adapter pending-confirm reset, selected-row glass normalization, sign-read compatibility hardening (array/NBT fallback), slot mapping fix, params reopen + no-args skip parity stabilization, translator-backed legacy block-id compat layer, runtime cursor progression/y-offset click-order hotfix, current-step menu-anchor fix, local TP-path parity for /testcase, legacy auto-TP to entry.z-2 before place, args-window rebind tolerance, custom menu-sign validation gate, skip=tp-only control-step parity, place-stage completion gate fix (no menu before real confirm), safe adapter dedup, and deep live diagnostics
files:
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/GameBridge.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/RuntimeCore.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/place/BlueGlassSearch.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/util/ReflectCompat.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/place/PlaceRuntimeStepExecutor.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/util/TestcaseTool.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/publish/PublishSignResolver.java
  - modern/fabric1165/src/main/java/com/rainbow_universe/bettercode/fabric1165/BetterCodeFabric1165.java
  - modern/fabric120/src/main/java/com/rainbow_universe/bettercode/fabric120/BetterCodeFabric120.java
  - modern/fabric121/src/main/java/com/rainbow_universe/bettercode/fabric121/BetterCodeFabric121.java
updated_at: 2026-02-19T20:45:00+07:00
next: run live 1.16.5 smoke with new jar and collect latest.log for MOD-107 gates: verify `inProgress` place results no longer enter OPEN_MENU early, confirm no old-sign clicks when new block is not yet placed, and continue GUI route-lag stabilization.
active_agents: []
