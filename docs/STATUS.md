# STATUS (MLBetterCode Mod)

## Current release baseline
- Legacy mod jar baseline: `bettercode-1.0.9`
- Active branch baseline: `main` (mod repo)

## Verified features
- `/mldsl run` plan execution path wired to place pipeline.
- Chest page merge logic stores and reuses merged snapshot across pages.
- `/loadmodule` supports explicit error reasons (timeout/http/ssl/etc.).
- Donor tier resolver from loaded file IDs available in hub module flow.

## Known regressions / risks
- Ongoing high-risk area: GUI/chest timing races on unstable server latency.
- Cross-branch divergence (`legacy/pages-work` vs `main`) not fully reconciled.
- Mixed working tree artifacts may appear during local experiments.

## Last user-tested
- Last manually confirmed by user: `resave2` multi-page save behavior marked as working in project notes.
- Re-verify after any chest paging code change.

## Notes
- This file is mod-only SoT. Do not put compiler/site operational details here.
