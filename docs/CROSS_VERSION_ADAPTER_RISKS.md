# CROSS VERSION ADAPTER RISKS (1.12.2 -> modern)

Format: `id | area | symptom | expected cause | required mitigation | status_1165 | status_120 | status_121`

- CV-001 | Legacy ids | `unknown block/item` during run | mismatch of 1.12.2 ids vs modern registry ids (`planks`, `nether_brick`, etc.) | central legacy->modern translation map with explicit trace logs | in_progress | in_progress | in_progress
- CV-002 | Menu routing | clicks go to player inventory instead of menu chest | slot match scans include player inventory rows | always skip player inventory for menu/args route matching | done | pending | pending
- CV-003 | Slot mapping | wrong slot clicked although item text matches | drift between `slot.id` / `slot.index` / container layout across versions | adapter-level slot normalization + click-time validation | in_progress | pending | pending
- CV-004 | Sign read | sign text missing/empty on valid sign | version/obfuscation differences in sign API and fields | multi-path read: API -> reflection -> NBT fallback, with source logs | in_progress | pending | pending
- CV-005 | Sign open target | right-click opens wrong sign/menu | y/z offset parity drift (`z-1`, `dy`) from legacy | strict legacy offset scan + fail-fast on sign absence | in_progress | pending | pending
- CV-006 | Place coordinates | block placed at wrong offset (`-1z`, `-2y`, etc.) | target plane mismatch (`entry` vs `entry.up/down`) | single coordinate contract + dual-plane confirm only where required | in_progress | pending | pending
- CV-007 | Control packets | `skip/newline/air` treated as normal place step | parser/runtime stage leakage of control tokens | early control short-circuit before place/menu/sign stages | in_progress | pending | pending
- CV-008 | TP settle | menu/click starts before teleport path settles | async tp path timing differs from legacy assumptions | settle gate and explicit stage completion checks | in_progress | pending | pending
- CV-009 | Window switch | args filled into stale/cached GUI then wiped | delayed real chest window under ping | stable non-empty window gate + windowId rebind handling | in_progress | pending | pending
- CV-010 | Performance | heavy tick lag (`Can't keep up`) during print | excessive hot-path scans/logs/click retries each tick | throttle traces, bounded retries, staged click cadence | in_progress | pending | pending
- CV-011 | Selector highlight | selected rows not visible in world | particle path not robust across contexts/settings | important-particle fallback + periodic visibility diagnostics | in_progress | pending | pending
- CV-012 | Seed detection | wrong start glass found or none found | insufficient seed probes for current map layout | layered probes: `-2,-1,-2`, fixed coord hint, barrier-corner fallback | in_progress | pending | pending
- CV-013 | Chunk/load guard | false missing block/sign while chunk not ready | checks execute before chunk/tile availability | chunk-loaded checks + bounded retry + explicit reason logs | in_progress | pending | pending
- CV-014 | Cache scope | wrong cached sign/route reused | keys miss dimension/scope separation | cache keys must include `dimension + scope + pos` | in_progress | pending | pending
- CV-015 | Obfuscation drift | adapter works in one target and breaks in another | field/method names differ by mappings/version | reflect-compat helpers with fallback chain and branch logging | in_progress | in_progress | in_progress
- CV-016 | 1:1 parity drift | behavior "almost same" but unstable live | mismatch in click order, cooldown, completion conditions | formal legacy parity checklist + smoke gate per target | in_progress | in_progress | in_progress

## Notes
- This file tracks adapter risks, not compiler internals.
- Update statuses only after live smoke evidence (logs + reproducible run).
