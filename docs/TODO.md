# TODO (MLBetterCode Mod)

Format: `id | task | priority | owner | needs_user_test | state | links`

- MOD-001 | Reconcile `legacy/pages-work` fixes into `main` via safe cherry-picks | P0 | agent | yes | open | branches:main,legacy/pages-work
- MOD-002 | Finalize robust chest paging logs (signal/no spam balance) | P1 | agent | yes | in_progress | src/main/java/com/example/examplemod/feature/regallactions
- MOD-003 | Implement mirror switch config (default vercel, optional duckdns mirror) | P1 | agent | yes | open | docs/AGENT_WORK_PLAN.md
- MOD-004 | Add donor tier output in compile/plan print summary | P1 | agent | yes | open | donaterequire.txt
- MOD-005 | Keep Fabric 1.20/1.21 structure aligned with legacy module boundaries | P2 | agent | no | open | modern/fabric120, modern/fabric121
- MOD-006 | Add docs scope CI guardrails and keep rules green | P1 | agent | no | done | tools/check_docs_scope.py
- MOD-007 | Complete modern runtime-core parity for publish/parser/printer across 1.16.5/1.20/1.21 (run+confirm partial wired) | P0 | agent | yes | in_progress | modern/core, modern/fabric1165, modern/fabric120, modern/fabric121
- MOD-008 | Wire real Forge 1.16.5 runtime adapter (commands/events/containers) on top of core | P0 | agent | yes | open | modern/forge1165
- MOD-009 | Add adapter contract tests (GameBridge invariants + scoreboard parsing + error codes) | P1 | agent | no | open | modern/core
- MOD-010 | Add packet-skip and slot-desync retry verification in modern smoke tests | P1 | agent | yes | open | modern/*
