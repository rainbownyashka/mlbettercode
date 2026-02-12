# modern/core

Shared platform-agnostic runtime contract for modern BetterCode targets.

Includes:
- `RuntimeCore` command flow entrypoints (`run`, `confirmload`, `publish`)
- `GameBridge` adapter interface
- `RuntimeErrorCode` model
- `SignLineNormalizer` utility for formatting-code-safe matching

This layer intentionally returns explicit `UNIMPLEMENTED_PLATFORM_OPERATION` until each adapter wires full parity logic.
