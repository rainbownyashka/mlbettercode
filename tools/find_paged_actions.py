#!/usr/bin/env python3
import argparse
import json
import pathlib
import re
from typing import Any, Dict, List, Tuple


OPEN_TEXT = "нажми, чтобы открыть"
NEXT_TEXT = "следующую страницу"


def strip_colors(text: str) -> str:
    if not text:
        return ""
    # Minecraft formatting codes: §x
    text = re.sub(r"§.", "", text)
    return text.replace("\u00a0", " ").strip().lower()


def is_next_page_arrow(slot: Dict[str, Any]) -> bool:
    reg = str(slot.get("registry", "")).lower()
    if reg != "minecraft:arrow":
        return False
    lore = slot.get("lore") or []
    lines = [strip_colors(str(x)) for x in lore]
    has_open = any(OPEN_TEXT in ln for ln in lines)
    has_next = any(NEXT_TEXT in ln for ln in lines)
    return has_open and has_next


def format_pos(pos: Dict[str, Any]) -> str:
    if not isinstance(pos, dict):
        return "(?, ?, ?)"
    return f"({pos.get('x', '?')}, {pos.get('y', '?')}, {pos.get('z', '?')})"


def find_paged_actions(data: Dict[str, Any]) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    results: List[Dict[str, Any]] = []
    rows_with_pages: List[Dict[str, Any]] = []
    for row in data.get("rows", []):
        row_idx = row.get("row")
        glass = row.get("glass") or {}
        row_hit = False
        for block in row.get("blocks", []):
            chest = block.get("chest") or {}
            slots = chest.get("slots") or []
            arrow_slots = [s for s in slots if is_next_page_arrow(s)]
            if not arrow_slots:
                continue
            row_hit = True
            sign = block.get("sign") or ["", "", "", ""]
            s1 = sign[0] if len(sign) > 0 else ""
            s2 = sign[1] if len(sign) > 1 else ""
            slot_indexes = sorted(int(s.get("slot", -1)) for s in arrow_slots)
            max_slot = max(slot_indexes) if slot_indexes else -1
            chest_size = int(chest.get("size", 0) or 0)
            detected_pages = (max_slot // chest_size + 1) if chest_size > 0 and max_slot >= 0 else None
            results.append(
                {
                    "row": row_idx,
                    "glass": glass,
                    "block_pos": block.get("pos") or {},
                    "chest_pos": chest.get("pos") or {},
                    "sign1": s1,
                    "sign2": s2,
                    "arrow_slots": slot_indexes,
                    "chest_size": chest_size,
                    "detected_pages": detected_pages,
                }
            )
        if row_hit:
            rows_with_pages.append({"row": row_idx, "glass": glass})
    return results, rows_with_pages


def main() -> int:
    ap = argparse.ArgumentParser(description="Find actions/conditions with next-page arrows in exportcode JSON")
    ap.add_argument("path", help="Path to exportcode JSON")
    args = ap.parse_args()

    p = pathlib.Path(args.path)
    if not p.is_file():
        raise FileNotFoundError(f"File not found: {p}")

    data = json.loads(p.read_text(encoding="utf-8"))
    results, rows = find_paged_actions(data)

    print(f"File: {p}")
    print(f"Rows total: {len(data.get('rows', []))}")
    print(f"Rows with pagination arrows: {len(rows)}")
    print(f"Actions/conditions with pagination arrows: {len(results)}")
    print("")
    if not results:
        print("No pagination arrows found.")
        return 0

    for i, item in enumerate(results, 1):
        print(
            f"{i}. row={item['row']} glass={format_pos(item['glass'])} block={format_pos(item['block_pos'])} chest={format_pos(item['chest_pos'])}"
        )
        print(
            f"   sign: [{item['sign1']}] [{item['sign2']}] | arrowSlots={item['arrow_slots']} | chestSize={item['chest_size']} | pages~={item['detected_pages']}"
        )

    # Ready-to-run hints for user
    rows_sorted = sorted({int(r["row"]) for r in rows if r.get("row") is not None})
    print("")
    print("Suggested resave flow with new logic:")
    print("1) /exportcodedebug")
    print("2) /codeselector")
    print("3) ПКМ по синему стеклу строк (rows): " + ", ".join(map(str, rows_sorted)))
    print("4) Нажми F (завершить выделение)")
    print("5) /module publish nocache")
    print("   (либо: /exportcode <floorsCSV> repaged_fix, если нужен только JSON)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

