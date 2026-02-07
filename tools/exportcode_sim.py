#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


CHEST_BLOCKS = {
    "minecraft:chest",
    "minecraft:trapped_chest",
    "minecraft:ender_chest",
    "minecraft:white_shulker_box",
    "minecraft:orange_shulker_box",
    "minecraft:magenta_shulker_box",
    "minecraft:light_blue_shulker_box",
    "minecraft:yellow_shulker_box",
    "minecraft:lime_shulker_box",
    "minecraft:pink_shulker_box",
    "minecraft:gray_shulker_box",
    "minecraft:silver_shulker_box",
    "minecraft:cyan_shulker_box",
    "minecraft:purple_shulker_box",
    "minecraft:blue_shulker_box",
    "minecraft:brown_shulker_box",
    "minecraft:green_shulker_box",
    "minecraft:red_shulker_box",
    "minecraft:black_shulker_box",
}


@dataclass(frozen=True)
class Pos:
    x: int
    y: int
    z: int

    def add(self, dx: int, dy: int, dz: int) -> "Pos":
        return Pos(self.x + dx, self.y + dy, self.z + dz)

    def up(self) -> "Pos":
        return self.add(0, 1, 0)

    def key(self) -> str:
        return f"{self.x},{self.y},{self.z}"

    def as_json(self) -> Dict[str, int]:
        return {"x": self.x, "y": self.y, "z": self.z}

    @staticmethod
    def from_json(obj: Dict[str, Any]) -> "Pos":
        return Pos(int(obj["x"]), int(obj["y"]), int(obj["z"]))


def _ensure_sign(sign: Optional[List[Any]]) -> List[str]:
    out = ["", "", "", ""]
    if isinstance(sign, list):
        for i in range(min(4, len(sign))):
            out[i] = str(sign[i] or "")
    return out


class WorldSim:
    def __init__(self, spec: Dict[str, Any]) -> None:
        raw_nodes = spec.get("nodes")
        if not isinstance(raw_nodes, dict):
            raise ValueError("spec.nodes must be an object: {'x,y,z': {...}}")
        self.nodes: Dict[str, Dict[str, Any]] = raw_nodes
        raw_cache = spec.get("chestCache") or {}
        if not isinstance(raw_cache, dict):
            raise ValueError("spec.chestCache must be an object")
        self.cache: Dict[str, Dict[str, Any]] = raw_cache
        self.scope = str(spec.get("scopeKey") or "SIM:0")

    def node(self, p: Pos) -> Dict[str, Any]:
        n = self.nodes.get(p.key())
        if isinstance(n, dict):
            return n
        return {"block": "minecraft:air"}

    def block(self, p: Pos) -> str:
        return str(self.node(p).get("block") or "minecraft:air")

    def sign_at_zminus1(self, entry: Pos) -> Optional[List[str]]:
        sign_pos = entry.add(0, 0, -1)
        n = self.node(sign_pos)
        sign = n.get("sign")
        if isinstance(sign, list):
            return _ensure_sign(sign)
        return None

    def chest_json(self, entry: Pos, prefer_cache: bool, dim: int = 0) -> Optional[Dict[str, Any]]:
        chest_pos = entry.up()
        chest_block = self.block(chest_pos)
        if chest_block not in CHEST_BLOCKS:
            return None

        node = self.node(chest_pos)
        chest = node.get("chest")
        title = ""
        size = 27
        live_slots: List[Dict[str, Any]] = []
        if isinstance(chest, dict):
            title = str(chest.get("title") or "")
            size = int(chest.get("size") or 27)
            slots = chest.get("slots") or []
            if isinstance(slots, list):
                live_slots = [s for s in slots if isinstance(s, dict)]

        best_slots = live_slots
        if prefer_cache and not live_slots:
            ck = f"{dim}:{chest_pos.key()}"
            cached = self.cache.get(ck)
            if isinstance(cached, dict):
                cslots = cached.get("slots") or []
                if isinstance(cslots, list) and cslots:
                    best_slots = [s for s in cslots if isinstance(s, dict)]
                    if not title:
                        title = str(cached.get("title") or "")
                    if size <= 0:
                        size = int(cached.get("size") or 27)

        return {
            "pos": chest_pos.as_json(),
            "title": title,
            "size": max(1, size),
            "slots": best_slots,
        }


def build_row(world: WorldSim, glass: Pos, row_index: int, max_steps: int, prefer_cache: bool, debug: bool) -> Tuple[Dict[str, Any], List[str]]:
    logs: List[str] = []
    blocks: List[Dict[str, Any]] = []
    empty_pairs = 0
    start = glass.up()
    if debug:
        logs.append(f"row[{row_index}] start glass={glass.key()} start={start.key()} maxSteps={max_steps}")

    for p in range(max_steps):
        entry = start.add(-2 * p, 0, 0)
        side = entry.add(1, 0, 0)
        entry_block = world.block(entry)
        side_block = world.block(side)
        sign = world.sign_at_zminus1(entry)
        sign_empty = sign is None or all((s or "").strip() == "" for s in sign)
        empty_slot = entry_block == "minecraft:air" and side_block == "minecraft:air" and sign_empty

        if empty_slot:
            empty_pairs += 1
            if debug:
                logs.append(f"row[{row_index}] p={p} emptySlot=true emptyPairs={empty_pairs} entry={entry.key()} side={side.key()}")
            if empty_pairs >= 2:
                if debug:
                    logs.append(f"row[{row_index}] stop@p={p} reason=two-empty-pairs")
                break
            continue

        empty_pairs = 0
        chest = world.chest_json(entry, prefer_cache=prefer_cache)
        has_entry = entry_block != "minecraft:air" or not sign_empty or chest is not None
        if debug:
            logs.append(
                f"row[{row_index}] p={p} entry={entry.key()} entryBlock={entry_block} sideBlock={side_block} "
                f"sign={sign if sign is not None else 'null'} chestDetected={chest is not None}"
            )

        if has_entry:
            obj: Dict[str, Any] = {"block": entry_block, "pos": entry.as_json()}
            if sign is not None:
                obj["sign"] = sign
            if chest is not None:
                obj["chest"] = chest
            blocks.append(obj)

        if has_entry and side_block in ("minecraft:piston", "minecraft:sticky_piston"):
            side_node = world.node(side)
            facing = str(side_node.get("facing") or "").lower()
            blocks.append({"block": side_block, "pos": side.as_json(), "facing": facing})

    row_obj = {"row": row_index, "glass": glass.as_json(), "blocks": blocks}
    if debug:
        logs.append(f"row[{row_index}] done blocks={len(blocks)}")
    return row_obj, logs


def run(spec: Dict[str, Any], max_steps: int, prefer_cache: bool, debug: bool) -> Tuple[Dict[str, Any], List[str]]:
    world = WorldSim(spec)
    glasses_raw = spec.get("glasses")
    if not isinstance(glasses_raw, list) or not glasses_raw:
        raise ValueError("spec.glasses must be a non-empty list of positions")
    glasses = [Pos.from_json(g) for g in glasses_raw if isinstance(g, dict)]
    out_rows: List[Dict[str, Any]] = []
    logs: List[str] = []
    for i, glass in enumerate(glasses):
        row, row_logs = build_row(world, glass, i, max_steps=max_steps, prefer_cache=prefer_cache, debug=debug)
        out_rows.append(row)
        logs.extend(row_logs)
    export = {
        "version": 2,
        "scopeKey": world.scope,
        "exportedAt": 0,
        "rows": out_rows,
    }
    return export, logs


def main() -> int:
    ap = argparse.ArgumentParser(description="Simulate BetterCode exportcode row scanning without Minecraft.")
    ap.add_argument("input", help="Path to simulator spec JSON")
    ap.add_argument("-o", "--out", default=None, help="Output exportcode JSON path")
    ap.add_argument("--max-steps", type=int, default=256, help="Max steps per row")
    ap.add_argument("--no-cache", action="store_true", help="Disable chest cache fallback")
    ap.add_argument("--debug", action="store_true", help="Print debug trace")
    args = ap.parse_args()

    src = Path(args.input).expanduser().resolve()
    if not src.exists():
        raise SystemExit(f"Input not found: {src}")
    spec = json.loads(src.read_text(encoding="utf-8"))
    export, logs = run(spec, max_steps=max(1, args.max_steps), prefer_cache=not args.no_cache, debug=args.debug)
    out_path = Path(args.out).expanduser().resolve() if args.out else src.with_suffix(".exportcode.json")
    out_path.write_text(json.dumps(export, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"OK: wrote {out_path}")
    if args.debug:
        for line in logs:
            print(line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

