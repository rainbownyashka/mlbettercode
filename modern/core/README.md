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
- `confirmload`: parses downloaded `plan.json` and reports entry count.
- `publish`: still explicit `UNIMPLEMENTED_PLATFORM_OPERATION` until full parity wiring.
