# STATUS (MLBetterCode Mod)

## Current release baseline
- Legacy mod jar baseline: `bettercode-1.0.9`
- Active branch baseline: `main` (mod repo)

## Verified features
- `/mldsl run` plan execution path wired to place pipeline.
- Chest page merge logic stores and reuses merged snapshot across pages.
- `/loadmodule` supports explicit error reasons (timeout/http/ssl/etc.).
- Donor tier resolver from loaded file IDs available in hub module flow.
- Modern runtime core scaffold added under `modern/core`:
  - shared `RuntimeCore`, `GameBridge`, `RuntimeErrorCode`, `SignLineNormalizer`.
  - `ScoreboardParser` + `ScoreboardContext` for explicit scoreboard-derived context logging.
  - explicit debug tags: `publish-debug`, `printer-debug`, `confirmload-debug`.
  - `/mldsl run` now performs real Hub download into `mldsl_modules/<postId>`.
  - `/loadmodule` alias added in modern Fabric targets (`/loadmodule <postId> [file]`).
  - `/confirmload` now parses downloaded `plan.json` and reports entries count.
- Modern targets now include bootstrap modules:
  - `modern/fabric1165` (new),
  - `modern/fabric120`,
  - `modern/fabric121`,
  - `modern/forge1165` (bootstrap jar, Forge hooks pending).

## Known regressions / risks
- Ongoing high-risk area: GUI/chest timing races on unstable server latency.
- Cross-branch divergence (`legacy/pages-work` vs `main`) not fully reconciled.
- Mixed working tree artifacts may appear during local experiments.
- Modern publish/parser/printer parity with legacy 1.12.2 is still incomplete.
- `forge1165` currently ships bootstrap-only artifact (no runtime Forge event wiring yet).

## Last user-tested
- Last manually confirmed by user: `resave2` multi-page save behavior marked as working in project notes.
- Re-verify after any chest paging code change.

## Notes
- This file is mod-only SoT. Do not put compiler/site operational details here.
