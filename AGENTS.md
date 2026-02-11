# AGENTS.md

## Purpose
These rules keep implementation readable during long sessions and reduce context loss.

## Rule 1: Intent Comment Before Non-Trivial Code
When adding or modifying non-trivial logic, add a short intent comment that explains:
- what this block does now;
- why it exists;
- what condition/event triggers it (if relevant).

Required format:
`// [TAG:<kebab-case-id>] <short intent>`

Example:
`// [TAG:gm-stick-click] Handle GM stick click for editor interaction flow.`

### Comment quality rules
- Keep comments short and specific.
- Do not add obvious comments like "assign variable".
- Add comments for retries, guards, async waits, cache/fallback decisions, merges, and branching behavior.
- If logic changes, update the nearby comment and keep tag stable when possible.

## Tags (searchability requirement)
Use searchable tags in all non-trivial intent comments:
`[TAG:<kebab-case-id>]`

Rules:
- Tag should be unique enough for fast search.
- Reuse stable tags for the same area over time.
- For split logic, use tag families:
`feature-open`, `feature-merge`, `feature-timeout`.

## Tool and Structure Registry
If you add a new script/tool/helper/migration, register it in:
- `docs/AGENT_TOOL_REGISTRY.md`

If you introduce new tag families/areas, register them in:
- `docs/AGENT_TAG_INDEX.md`

## Persistent Work Plan
For substantial tasks, keep/update a compact plan in:
- `docs/AGENT_WORK_PLAN.md`
- Start from `docs/AGENT_PROJECT_OVERVIEW.md` to restore project context before edits.

Minimum plan fields:
- objective;
- active steps;
- blockers/assumptions;
- next action.

## End-of-Change Discipline
After major changes, update:
- tool registry (if tools/scripts were added/changed),
- tag index (if new tag families were introduced),
- work plan status.

## Scope
Rules apply to:
- Java gameplay/mod logic;
- scripts in `tools/`;
- workflow docs.

## Cross-Version Structure Rule
Keep project structure and module boundaries as similar as possible across versions (legacy112/fabric120/fabric121):
- prefer same feature names and folder semantics;
- keep analogous entrypoints/service layers where platform allows;
- avoid unnecessary divergence in architecture and naming.

Goal: feature parity work should be mostly porting, not re-inventing.

## Priority
If this file conflicts with older informal habits, this file takes precedence.
