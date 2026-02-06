# exportcode -> MLDSL draft converter

Quick use:

```powershell
python tools/exportcode_to_mldsl.py "%APPDATA%\\.minecraft\\exportcode_demo_123.json"
```

With custom output:

```powershell
python tools/exportcode_to_mldsl.py in.json -o out.mldsl
```

What it does:

- Reads `rows[].blocks[]` from `exportcode_*.json`.
- Converts known row starters to MLDSL blocks:
  - `diamond_block/gold_block` -> `event(...) {}`
  - `lapis_block` -> `func(...) {}`
  - `emerald_block` -> `loop(..., ticks) {}`
- Converts chest slots to args in `slotraw(N)=...` format.
- Detects enum-like lore bullets (`?/0`) and emits `clicks(slot,n)=0` when needed.
- Emits comments for unsupported blocks instead of silently dropping data.

Note:

This is a deterministic draft converter. It preserves enough structure/args to finish manually in MLDSL.
