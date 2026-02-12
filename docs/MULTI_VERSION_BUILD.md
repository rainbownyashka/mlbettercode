# Multi-Version Build (Legacy + Modern Targets)

This repo has these build targets:

- `legacy112` - existing Forge 1.12.2 mod (full BetterCode).
- `fabric1165` - Fabric 1.16.5 bootstrap with shared modern core.
- `fabric120` - new Fabric 1.20.1 minimal feature set (printer/export selector bootstrap).
- `fabric121` - Fabric 1.21.1 minimal feature set (same bootstrap commands).
- `forge1165` - Forge 1.16.5 bootstrap artifact (runtime Forge hooks pending).

## Build matrix command

Use one command from repo root:

```powershell
python tools/build_matrix.py legacy112
python tools/build_matrix.py fabric1165
python tools/build_matrix.py fabric120
python tools/build_matrix.py fabric121
python tools/build_matrix.py forge1165
python tools/build_matrix.py modern_all
python tools/build_matrix.py all
```

Optional custom Gradle task:

```powershell
python tools/build_matrix.py fabric120 --task runClient
```

Notes:

- `legacy112` requires JDK 8.
- `fabric1165` uses Loom and requires JDK 17 runtime (bytecode target remains Java 8).
- `forge1165` bootstrap build requires JDK 17 runtime (bytecode target remains Java 8).
- `fabric120` requires JDK 17.
- `fabric121` requires JDK 21.
- `modern_all` builds: `fabric1165`, `fabric120`, `fabric121`, `forge1165`.
- `all` keeps legacy compatibility behavior: `legacy112`, `fabric120`, `fabric121`.

## Modern command set (bootstrap)

- `/mldsl run <postId> [config]`
- `/confirmload`
- `/module publish`
- `/bc_print_plan [path]`

Current state is explicit bootstrap for runtime parity migration:
- shared runtime contract and debug tags in `modern/core`,
- `/mldsl run` downloads files from Hub and stores them in `mldsl_modules/<postId>`,
- `/confirmload` parses downloaded `plan.json` and prints entries count,
- transparent `UNIMPLEMENTED_PLATFORM_OPERATION` errors where place/publish parity is not yet wired.
