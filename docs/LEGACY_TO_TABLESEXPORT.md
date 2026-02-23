# Legacy -> TablesExport Converter

Purpose: convert legacy `regallactions_export.txt` (1.12.2) into lightweight `tablesexport.txt` shape for cross-version name-based parity checks.

## Script

- `tools/legacy_regallactions_to_tablesexport.py`

## Default mode (recommended)

Name-first export, without `itemId`:

```bash
python tools/legacy_regallactions_to_tablesexport.py \
  --in "C:\Users\ASUS\AppData\Roaming\.minecraft\regallactions_export.txt" \
  --out "C:\Users\ASUS\AppData\Roaming\.minecraft\mldsl_tables\1.12.2\tablesexport.from_legacy.names.txt" \
  --item-id-mode none
```

Output fields per record:

- `path=<legacy sign1 group>`
- `item=<subitem title>` (fallback: `sign2`)
- `type=action`

`itemId` is omitted in this mode.

## Optional legacy item ids

If needed for diagnostics only:

```bash
python tools/legacy_regallactions_to_tablesexport.py \
  --in "<legacy file>" \
  --out "<out file>" \
  --item-id-mode legacy
```

## Notes

- Converter deduplicates by default (`path + item + itemId + type`).
- Use `--no-dedupe` to keep all rows.
- This converter is for parity tooling; it does not change runtime behavior.
