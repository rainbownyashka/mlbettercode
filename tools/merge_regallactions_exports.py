#!/usr/bin/env python3
"""
Merge two regallactions_export.txt files.

Base file keeps all old records.
Update file replaces matching records and appends brand-new ones.

Matching strategy (in order):
1) full key: path + category + subitem + gui + sign1..sign4
2) loose key: category + subitem + gui + sign1..sign4 (only if unique in base)
3) very loose key: category + subitem (only if unique in base)
"""

from __future__ import annotations

import argparse
import datetime as dt
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Tuple


@dataclass
class Record:
    path: str = ""
    category: str = ""
    subitem: str = ""
    gui: str = ""
    sign1: str = ""
    sign2: str = ""
    sign3: str = ""
    sign4: str = ""
    has_chest: str = "false"
    items: List[str] = field(default_factory=list)
    extras: List[Tuple[str, str]] = field(default_factory=list)

    def full_key(self) -> Tuple[str, ...]:
        return (
            self.path.strip(),
            self.category.strip(),
            self.subitem.strip(),
            self.gui.strip(),
            self.sign1.strip(),
            self.sign2.strip(),
            self.sign3.strip(),
            self.sign4.strip(),
        )

    def loose_key(self) -> Tuple[str, ...]:
        return (
            self.category.strip(),
            self.subitem.strip(),
            self.gui.strip(),
            self.sign1.strip(),
            self.sign2.strip(),
            self.sign3.strip(),
            self.sign4.strip(),
        )

    def very_loose_key(self) -> Tuple[str, ...]:
        return (self.category.strip(), self.subitem.strip())


def parse_export(path: Path) -> List[Record]:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    out: List[Record] = []
    current: Record | None = None

    def flush() -> None:
        nonlocal current
        if current is not None:
            out.append(current)
        current = None

    for raw in lines:
        line = raw.rstrip("\n\r")
        if line.startswith("# record"):
            flush()
            current = Record()
            continue
        if line.startswith("records="):
            continue
        if current is None:
            continue
        if not line:
            continue
        if "=" not in line:
            current.extras.append((line, ""))
            continue
        key, val = line.split("=", 1)
        if key == "path":
            current.path = val
        elif key == "category":
            current.category = val
        elif key == "subitem":
            current.subitem = val
        elif key == "gui":
            current.gui = val
        elif key == "sign1":
            current.sign1 = val
        elif key == "sign2":
            current.sign2 = val
        elif key == "sign3":
            current.sign3 = val
        elif key == "sign4":
            current.sign4 = val
        elif key == "hasChest":
            current.has_chest = val
        elif key == "item":
            current.items.append(val)
        else:
            current.extras.append((key, val))
    flush()
    return out


def dump_export(path: Path, records: List[Record]) -> None:
    out: List[str] = [f"records={len(records)}", ""]
    for i, r in enumerate(records, start=1):
        out.append(f"# record {i}")
        out.append(f"path={r.path}")
        out.append(f"category={r.category}")
        out.append(f"subitem={r.subitem}")
        out.append(f"gui={r.gui}")
        out.append(f"sign1={r.sign1}")
        out.append(f"sign2={r.sign2}")
        out.append(f"sign3={r.sign3}")
        out.append(f"sign4={r.sign4}")
        out.append(f"hasChest={r.has_chest}")
        for k, v in r.extras:
            if v:
                out.append(f"{k}={v}")
            else:
                out.append(k)
        for item in r.items:
            out.append(f"item={item}")
        if i != len(records):
            out.append("")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def build_index(records: List[Record], key_fn) -> Dict[Tuple[str, ...], List[int]]:
    idx: Dict[Tuple[str, ...], List[int]] = {}
    for i, rec in enumerate(records):
        idx.setdefault(key_fn(rec), []).append(i)
    return idx


def merge_records(base: List[Record], update: List[Record]) -> Tuple[List[Record], Dict[str, int]]:
    merged = list(base)
    stats = {"replaced_full": 0, "replaced_loose": 0, "replaced_very_loose": 0, "appended": 0}

    full_idx = build_index(merged, lambda r: r.full_key())
    loose_idx = build_index(merged, lambda r: r.loose_key())
    vloose_idx = build_index(merged, lambda r: r.very_loose_key())

    for rec in update:
        fk = rec.full_key()
        lk = rec.loose_key()
        vk = rec.very_loose_key()

        if fk in full_idx and full_idx[fk]:
            pos = full_idx[fk][0]
            merged[pos] = rec
            stats["replaced_full"] += 1
            continue
        if lk in loose_idx and len(loose_idx[lk]) == 1:
            pos = loose_idx[lk][0]
            merged[pos] = rec
            stats["replaced_loose"] += 1
            continue
        if vk in vloose_idx and len(vloose_idx[vk]) == 1:
            pos = vloose_idx[vk][0]
            merged[pos] = rec
            stats["replaced_very_loose"] += 1
            continue

        merged.append(rec)
        stats["appended"] += 1

    return merged, stats


def backup_file(path: Path) -> Path:
    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    bak = path.with_suffix(path.suffix + f".bak_{stamp}")
    bak.write_bytes(path.read_bytes())
    return bak


def main() -> int:
    ap = argparse.ArgumentParser(description="Merge regallactions export files with backup.")
    ap.add_argument("--base", required=True, help="Base export file (kept, updated in place).")
    ap.add_argument("--update", required=True, help="Update export file (replaces/adds into base).")
    ap.add_argument("--out", default="", help="Optional output path. If omitted, base is overwritten.")
    args = ap.parse_args()

    base_path = Path(args.base).expanduser()
    upd_path = Path(args.update).expanduser()
    out_path = Path(args.out).expanduser() if args.out else base_path

    if not base_path.exists():
        raise FileNotFoundError(f"Base file not found: {base_path}")
    if not upd_path.exists():
        raise FileNotFoundError(f"Update file not found: {upd_path}")

    base_records = parse_export(base_path)
    upd_records = parse_export(upd_path)

    merged, stats = merge_records(base_records, upd_records)

    backup = backup_file(base_path) if out_path == base_path else None
    dump_export(out_path, merged)

    print(f"base_records={len(base_records)} update_records={len(upd_records)} merged_records={len(merged)}")
    print(
        "replaced_full={replaced_full} replaced_loose={replaced_loose} "
        "replaced_very_loose={replaced_very_loose} appended={appended}".format(**stats)
    )
    if backup is not None:
        print(f"backup={backup}")
    print(f"written={out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
