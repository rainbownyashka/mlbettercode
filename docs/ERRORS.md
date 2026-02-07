# Error Codes and Diagnostics

## Export diagnostics
- `logic=v4 entryStep=-2x side=-1x noFallback exportSidePistonEvenWithoutEntry`
- Printed at row start in debug mode.

## Common runtime issues
- `two-empty-pairs`
- Meaning: row tail reached; iterator stop condition.
- Action: verify selected row and nearby loaded chunks.

- `unloaded entry/side`
- Meaning: row scanning hit unloaded positions.
- Action: move closer / ensure chunk loaded.

## MLDSL invocation issues
- `Converter failed (code=...)`
- Action: verify `mldsl.exe` version and supported CLI commands.

- `Compile failed (code=...)`
- Action: inspect full stderr printed by BetterCode debug logs.
