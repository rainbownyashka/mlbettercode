# AGENT_TOOL_REGISTRY

Purpose: keep a persistent inventory of project scripts/tools and their intent.

## Existing Tools (initial inventory)
- `build_watcher.py`
Purpose: build/watch helper for local iteration.

- `diffgui.py`
Purpose: GUI-related diff/inspection helper.

- `nm.py`
Purpose: project-specific utility script (name-based helper; inspect before changes).

## Usage Rule
When adding or changing a script/tool, record:
- path;
- purpose;
- expected input/output;
- safety notes (if destructive).

