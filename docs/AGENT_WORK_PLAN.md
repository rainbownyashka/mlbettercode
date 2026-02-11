# AGENT_WORK_PLAN

## Objective
Keep persistent project state between sessions: what is tested, what is broken, what is fixed, and what to do next.

## Current Focus
- Stabilize `regallactions resave2` so multi-page action GUIs are saved and merged reliably.
- Prevent cross-flow interference (extra clicks/replays/old chest context).
- Keep logs clear enough for fast root-cause isolation without excessive spam.
- Define modern (Fabric 1.20/1.21) roadmap toward core feature parity with legacy flows.

## Last Confirmed User Feedback (Summary)
- In multiple runs, only last page was effectively persisted in output despite page traversal logs.
- Transient empty GUI states occur during page turn.
- Need strict transparency in logs for each decision/wait/save stage.
- Requested: inspect multi-version generation (1.20/1.21) and sync project docs to actual build flow.
- Requested: plan full modern feature roadmap; exclude `regallactions`; keep `copycode` as lowest priority.

## Active Steps
1. Audit resave2 flow end-to-end for external state interference.
2. Validate page detection/saving boundaries against merged snapshot source.
3. Add/keep deterministic chat+log checkpoints for open/wait/page/save/finish.
4. Re-test against user-provided logs and export files.
5. Verify new Forge mirror settings for hub publish/load URLs in live run.
6. Keep multi-version docs aligned with real build matrix script and Java requirements.
7. Prepare staged implementation plan for Fabric 1.20/1.21 parity (without regallactions).

## Blockers / Assumptions
- Final correctness must be validated in live server conditions (user runtime).
- Some GUI timing is server-latency dependent; waits/guards must account for it.

## Next Action
Run targeted verification on `regallactions resave2` and confirm merged result contains all expected page arguments.

## TODO Backlog
- Add compile-time donor requirements report:
  - Parse MLDSL plan/export for used donor action IDs.
  - Resolve required donor tier(s) via `donaterequire.txt`.
  - Print colored console summary (Colorama-style) for plan sharing/printing.
- Add website documentation docs (architecture/deploy/maintenance pages).
- Add short RU doc section for `hubPublishBaseUrl` and `hubLoadBaseUrl` mirror setup.
- Add compact troubleshooting matrix for common GUI/chest failure patterns.
- Add checklist for “safe re-export required” detection conditions.
- Evaluate Umami telemetry from mod side: anonymous online count trend only (no nicknames/PII), for later testing.
- If implemented, test visual/animated online metric presentation on site using only aggregated mod presence data.
- Modern Fabric roadmap (1.20/1.21), target parity modules:
  - P0: pages-aware print pipeline (`/mldsl run` + `/placeadvanced` analog + chest page traversal/merge for params).
  - P0: `/module publish` flow (bundle generation + open publish endpoint), with mirror switch behavior.
  - P1: selection subsystem + selection stick tool (wand) for row/block selection workflows.
  - P1: export pipeline parity (`/exportcode` + selected rows behavior).
  - P2 (last): `/copycode` parity.
  - Explicitly out of scope for modern parity: `regallactions`.

## Update Rule
After each meaningful test cycle, append:
- input (what command/scenario);
- observed result (logs/export outcome);
- decision (fix / hypothesis / next step).
