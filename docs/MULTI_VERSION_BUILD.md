# Multi-Version Build (start with Fabric 1.20.1)

This repo now has two build targets:

- `legacy112` - existing Forge 1.12.2 mod (full BetterCode).
- `fabric120` - new Fabric 1.20.1 minimal feature set (printer/export selector bootstrap).

## Build matrix command

Use one command from repo root:

```powershell
python tools/build_matrix.py legacy112
python tools/build_matrix.py fabric120
python tools/build_matrix.py all
```

Optional custom Gradle task:

```powershell
python tools/build_matrix.py fabric120 --task runClient
```

## Fabric 1.20.1 command set (initial)

- `/bc_select` - toggle current looked-at block in selection
- `/bc_select_clear` - clear selected blocks
- `/bc_select_list` - list selected blocks
- `/bc_export_selected [name]` - save selected rows to `exportcode_<name>_<ts>.json`
- `/bc_print_plan [path]` - parse `plan.json` and print `entries` count

This is the base layer for later parity with legacy printer/export pipeline.

