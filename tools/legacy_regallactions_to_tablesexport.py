#!/usr/bin/env python3
"""
Convert legacy 1.12.2 regallactions_export.txt into lightweight tablesexport.txt format.

Default behavior is name-first parity:
- keeps action name in `item=...`
- does NOT emit itemId by default

Use `--item-id-mode legacy` to include legacy item id parsed from `subitem=[minecraft:... meta=...]`.
"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple


@dataclass
class LegacyRecord:
    path: str = ""
    category: str = ""
    subitem: str = ""
    sign1: str = ""
    sign2: str = ""


@dataclass
class OutRecord:
    path: str
    item: str
    item_id: str
    type: str = "action"


def strip_mc_format(text: str) -> str:
    if not text:
        return ""
    text = text.replace("\\n", " ")
    text = re.sub(r"§.", " ", text, flags=re.IGNORECASE)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def parse_legacy(path: Path) -> List[LegacyRecord]:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    out: List[LegacyRecord] = []
    cur: LegacyRecord | None = None

    def flush() -> None:
        nonlocal cur
        if cur is not None:
            out.append(cur)
        cur = None

    for raw in lines:
        line = raw.rstrip("\r\n")
        if line.startswith("# record"):
            flush()
            cur = LegacyRecord()
            continue
        if line.startswith("records="):
            continue
        if cur is None or not line or "=" not in line:
            continue
        k, v = line.split("=", 1)
        if k == "path":
            cur.path = v
        elif k == "category":
            cur.category = v
        elif k == "subitem":
            cur.subitem = v
        elif k == "sign1":
            cur.sign1 = v
        elif k == "sign2":
            cur.sign2 = v
    flush()
    return out


def parse_subitem_title(subitem: str) -> str:
    # subitem=[minecraft:foo meta=0] §aTitle | desc...
    s = re.sub(r"^\[[^\]]+\]\s*", "", subitem).strip()
    s = strip_mc_format(s)
    if "|" in s:
        s = s.split("|", 1)[0].strip()
    return s


def parse_subitem_item_id(subitem: str) -> str:
    m = re.search(r"\[(minecraft:[a-z0-9_]+)\s+meta=", subitem)
    return m.group(1) if m else ""


def convert(records: List[LegacyRecord], item_id_mode: str, dedupe: bool) -> Tuple[List[OutRecord], Dict[str, int]]:
    out: List[OutRecord] = []
    seen = set()
    dropped_empty = 0
    dropped_dupe = 0

    for r in records:
        # Use normalized subitem title as primary name, sign2 as fallback.
        item_name = parse_subitem_title(r.subitem)
        if not item_name:
            item_name = strip_mc_format(r.sign2)
        if not item_name:
            dropped_empty += 1
            continue

        item_id = parse_subitem_item_id(r.subitem) if item_id_mode == "legacy" else ""
        group_path = strip_mc_format(r.sign1) or strip_mc_format(r.path)

        rec = OutRecord(path=group_path, item=item_name, item_id=item_id, type="action")
        key = (rec.path, rec.item, rec.item_id, rec.type)
        if dedupe and key in seen:
            dropped_dupe += 1
            continue
        seen.add(key)
        out.append(rec)

    stats = {
        "input_records": len(records),
        "output_records": len(out),
        "dropped_empty": dropped_empty,
        "dropped_dupe": dropped_dupe,
    }
    return out, stats


def dump_tablesexport(path: Path, records: List[OutRecord], item_id_mode: str) -> None:
    lines: List[str] = []
    lines.append("reason=converted_from_legacy_regallactions")
    lines.append("dimension=legacy:1.12.2")
    lines.append("sign=0,0,0")
    lines.append("records=" + str(len(records)))
    for i, r in enumerate(records, start=1):
        lines.append("")
        lines.append(f"# record {i}")
        lines.append("path=" + r.path)
        lines.append("item=" + r.item)
        if item_id_mode == "legacy":
            lines.append("itemId=" + r.item_id)
        lines.append("type=" + r.type)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description="Convert legacy regallactions export to tablesexport format.")
    ap.add_argument("--in", dest="in_path", required=True, help="Path to regallactions_export.txt")
    ap.add_argument("--out", dest="out_path", required=True, help="Output tablesexport path")
    ap.add_argument(
        "--item-id-mode",
        choices=["none", "legacy"],
        default="none",
        help="Emit itemId field: none (default) or legacy.",
    )
    ap.add_argument(
        "--no-dedupe",
        action="store_true",
        help="Keep duplicate rows (default: dedupe by path+item+itemId+type).",
    )
    args = ap.parse_args()

    in_path = Path(args.in_path).expanduser()
    out_path = Path(args.out_path).expanduser()
    if not in_path.exists():
        raise FileNotFoundError(f"Input file not found: {in_path}")

    legacy_records = parse_legacy(in_path)
    out_records, stats = convert(
        records=legacy_records,
        item_id_mode=args.item_id_mode,
        dedupe=not args.no_dedupe,
    )
    dump_tablesexport(out_path, out_records, args.item_id_mode)

    print(f"in={in_path}")
    print(f"out={out_path}")
    print(f"item_id_mode={args.item_id_mode}")
    print(
        "input_records={input_records} output_records={output_records} "
        "dropped_empty={dropped_empty} dropped_dupe={dropped_dupe}".format(**stats)
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

