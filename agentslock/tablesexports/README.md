# Table Exports Layout

Versioned snapshots for menu/action exports are stored here to track parity over time.

- `1.12.2/tablesexport.from_legacy.names.txt` - generated from legacy `regallactions_export.txt` (name-based parity baseline, no item ids)
- `1.16.5/tablesexport.latest.txt` - latest checked snapshot
- `1.16.5/tablesexport_YYYYMMDD_HHMMSS.txt` - immutable timestamped snapshots

Recommended same layout in `.minecraft`:

- `.minecraft/mldsl_tables/1.16.5/tablesexport.latest.txt`
- `.minecraft/mldsl_tables/1.16.5/tablesexport_YYYYMMDD_HHMMSS.txt`
