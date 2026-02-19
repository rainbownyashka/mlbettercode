# Agent Lock

task_id: MOD-091+MOD-092+MOD-094+MOD-095+MOD-096+MOD-097+MOD-098
owner: codex
status: in_progress
scope: 1:1 parity migration; run/publish parity with adapter pending-confirm reset, selected-row glass normalization, sign-read compatibility hardening + NBT fallback, slot mapping fix, safe adapter dedup, and deep live diagnostics
files:
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/place/BlueGlassSearch.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/util/ReflectCompat.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/place/PlaceRuntimeStepExecutor.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/publish/PublishSignResolver.java
  - modern/fabric1165/src/main/java/com/rainbow_universe/bettercode/fabric1165/BetterCodeFabric1165.java
  - modern/fabric120/src/main/java/com/rainbow_universe/bettercode/fabric120/BetterCodeFabric120.java
  - modern/fabric121/src/main/java/com/rainbow_universe/bettercode/fabric121/BetterCodeFabric121.java
updated_at: 2026-02-19T17:27:00+03:00
next: validate latest fabric1165 hotfix in live smoke (real slot ids/indexes in menu snapshots, no #-1 idx=-1 route failures, sign read non-empty via reflection/nbt fallback), then continue menu ack/path stability if any blocker remains
