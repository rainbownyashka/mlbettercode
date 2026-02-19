# Agent Lock

task_id: MOD-091+MOD-092+MOD-094+MOD-095+MOD-096+MOD-097+MOD-098+MOD-099+MOD-100+MOD-101+MOD-102
owner: codex
status: in_progress
scope: 1:1 parity migration; run/publish parity with adapter pending-confirm reset, selected-row glass normalization, sign-read compatibility hardening (array/NBT fallback), slot mapping fix, params reopen + no-args skip parity stabilization, translator-backed legacy block-id compat layer, safe adapter dedup, and deep live diagnostics
files:
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/place/BlueGlassSearch.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/util/ReflectCompat.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/place/PlaceRuntimeStepExecutor.java
  - modern/core/src/main/java/com/rainbow_universe/bettercode/core/publish/PublishSignResolver.java
  - modern/fabric1165/src/main/java/com/rainbow_universe/bettercode/fabric1165/BetterCodeFabric1165.java
  - modern/fabric120/src/main/java/com/rainbow_universe/bettercode/fabric120/BetterCodeFabric120.java
  - modern/fabric121/src/main/java/com/rainbow_universe/bettercode/fabric121/BetterCodeFabric121.java
updated_at: 2026-02-19T18:24:00+03:00
next: validate latest fabric1165 follow-up in live smoke (event/lead args=no -> SKIP_PARAMS_NO_ARGS, no INVALID_BLOCK_ID on planks, block_id_compat mode=vanilla_flattening/compat_map visible, lower periodic tick freezes, sign read resolved via array_fallback/nbt), then continue remaining 1:1 menu/path parity gaps if any
