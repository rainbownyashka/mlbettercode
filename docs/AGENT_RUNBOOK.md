# Agent Runbook (Persistent Context)

This file is a compact operational memory for future coding sessions.
Keep it updated when workflow changes.

## 1) Repositories and roles

- `k:\mymod`
  - BetterCode mod (Minecraft client mod, legacy 1.12.2 + modern branches).
  - Main file: `src/main/java/com/example/examplemod/ExampleMod.java`
- `C:\Users\ASUS\Documents\mlctmodified`
  - MLDSL compiler/translator (`mldsl_cli.py`, `mldsl_compile.py`, `mldsl_exportcode.py`).
  - Installer/payload scripts and release automation.
- `mldsl-hub` (separate site repo, Cloudflare Pages + Functions)
  - Social sharing portal for MLDSL modules.

## 2) BetterCode mod build (1.12.2)

Run from `k:\mymod`:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon clean jar
```

Output jar:

- `k:\mymod\build\libs\bettercode-1.0.9.jar`

## 3) Multi-version builds

Documented in `docs/MULTI_VERSION_BUILD.md`.

Typical:

```powershell
python tools/build_matrix.py legacy112
python tools/build_matrix.py fabric120
python tools/build_matrix.py fabric121
```

## 4) Export/parser logic rules (important)

Current strict row parsing intent:

- Entry iterator moves by `-2x`.
- Side/bracket check uses only `-1x` from current entry.
- No fallback to `+1x` unless explicitly requested by user.
- Piston semantics:
  - `facing=west` => open brace
  - `facing=east` => close brace

If user says "no fallback", do not add hidden fallback paths.

## 5) In-game debug commands that matter

- `/exportcodedebug on`
- `/exportcode [floorsCSV] [name]`
- `/module publish [name] [nocache]`
- `/selectfloor <1..20>`

Check logs for:

- `logic=... entryStep=... side=... noFallback ...`
- row iteration (`p=...`) and detected pistons/chests.

## 6) MLDSL translator/compiler local workflow

Run from `C:\Users\ASUS\Documents\mlctmodified`:

```powershell
python .\mldsl_cli.py exportcode "<input_exportcode.json>" -o "<out.mldsl>"
python .\mldsl_cli.py compile "<out.mldsl>" --plan "<out.plan.json>"
```

If running from another directory, `api_aliases.json` may fail to resolve.
Prefer running CLI from repo root above.

## 7) MLDSL build/install artifacts

Main files:

- `mldsl_cli.py`
- `mldsl_compile.py`
- `mldsl_exportcode.py`

Installer payload script:

- `packaging/prepare_installer_payload.py`

If creating Windows executable via Nuitka:

```powershell
python -m nuitka mldsl_cli.py --standalone --assume-yes-for-downloads --output-dir=dist --output-filename=mldsl.exe
```

## 8) Known failure signatures

- `api_aliases.json not found`
  - Cause: wrong working dir when running `mldsl_cli.py`.
- `unknown event` during compile
  - Usually catalog mismatch or invalid event header in generated mldsl.
- Mojibake in game messages
  - Check source string encoding and runtime formatting path.
- `/module publish` exports too many rows
  - Selection empty; command falls back to floors/scan logic.

## 9) Quick regression test checklist

1. Build mod jar.
2. In game, select exactly one row with Code Selector.
3. Run `/module publish nocache` and `/exportcodedebug on`.
4. Verify exported row block order and piston directions.
5. Convert export JSON with MLDSL CLI.
6. Compile generated `.mldsl` to `.plan.json`.
7. Ensure braces/conditions are valid (`if_player... { ... }`).

## 10) Site/hub operational notes

Hub is separate from this repo. Keep in mind:

- Cloudflare Pages deploy by git push.
- DB migrations required when schema changes.
- Auth providers currently include Discord/Google (check env vars before debugging auth).

Do not assume hub code exists in `k:\mymod`.

