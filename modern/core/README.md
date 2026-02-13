# modern/core

Shared platform-agnostic runtime contract for modern BetterCode targets.

Includes:
- `RuntimeCore` command flow entrypoints (`run`, `confirmload`, `publish`)
- `GameBridge` adapter interface
- `RuntimeErrorCode` model
- `SignLineNormalizer` utility for formatting-code-safe matching

Current migration state:
- `run`: downloads module files + `plan.json` from Hub into local `mldsl_modules/<postId>`.
- `loadmodule`: explicit alias flow with optional file name (`/loadmodule <postId> [file]`).
- `confirmload`: parses downloaded `plan.json` and dispatches `/placeadvanced ...` commands via adapter command bridge.
- `publish`: prepares local publish bundle in `runDir/mldsl_publish/bundle_<postId>_<ts>` with copied files + `publish_meta.json`.
- `modsettings`: persisted runtime settings (`bettercode_modern_config.json`) for `hub.useMirror`, timeouts, debug, and printer knobs.
