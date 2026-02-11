# README_FOR_AGENTS_CONTRIBUTING_RULES

Purpose: keep implementation understandable over long sessions and reduce context loss.

## Rule 1: comment intent before code blocks
When adding or modifying code, add a short comment that states:
- what is being done now;
- why this block exists;
- what event/condition triggers it (if relevant).

Format:
`// [TAG:short-name] <intent sentence>`

Example:
`// [TAG:gm-stick-click] Create GM stick click handler for editor interactions.`

Comment requirements:
- keep comments short and specific;
- no obvious comments like "assign variable";
- add comment for non-trivial logic, retries, guards, async waits, cache decisions, merges;
- when modifying existing logic, update nearby comment and keep tag stable.

## Tags (required for non-trivial blocks)
Use searchable tags in comments:
`[TAG:<kebab-case-id>]`

Tag rules:
- tag must be unique enough for fast search;
- keep one stable tag for the same area over time;
- if logic is split, reuse tag family: `feature-x-open`, `feature-x-merge`, `feature-x-timeout`.

## Tool/change journaling (required)
When creating a script/tool/helper/migration utility, register it in:
- `docs/AGENT_TOOL_REGISTRY.md`

When introducing new structural tag areas, register them in:
- `docs/AGENT_TAG_INDEX.md`

## Work planning persistence
For big tasks, keep a compact running plan/checklist in:
- `docs/AGENT_WORK_PLAN.md`

Minimum entries:
- objective;
- active steps;
- blockers/assumptions;
- next action.

## Summary discipline
At end of a major change, update:
- tool registry (if new tool used/added);
- tag index (if new tag family added);
- work plan status.

## Scope
These rules apply to:
- Java gameplay logic;
- scripts in `tools/`;
- docs that describe workflows.

## Priority
If this file conflicts with older informal habits, this file wins.
