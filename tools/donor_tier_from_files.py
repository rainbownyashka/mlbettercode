#!/usr/bin/env python3
"""
Resolve required donor tier from file contents by action-id presence.
Rule: if no known ID is found -> gamer.
"""

from __future__ import annotations

import argparse
import pathlib
import re
from typing import Dict, List, Set, Tuple


TIER_ORDER = ["gamer", "skilled", "expert", "hero", "king", "legend"]


def load_rules(path: pathlib.Path) -> Dict[str, Set[str]]:
    out: Dict[str, Set[str]] = {t: set() for t in TIER_ORDER}
    cur = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        low = line.lower()
        if low.endswith(" can"):
            name = low[:-4].strip()
            cur = name if name in out else None
            continue
        if cur and line.isdigit():
            out[cur].add(line)
    return out


def extract_ids(text: str) -> Set[str]:
    return set(re.findall(r"\b\d{1,6}\b", text))


def resolve_tier(ids: Set[str], rules: Dict[str, Set[str]]) -> Tuple[str, Set[str]]:
    best = "gamer"
    matched: Set[str] = set()
    best_rank = 0
    for i, tier in enumerate(TIER_ORDER):
        inter = ids & rules.get(tier, set())
        if inter:
            matched |= inter
            if i >= best_rank:
                best_rank = i
                best = tier
    return best, matched


def collect_files(inputs: List[str]) -> List[pathlib.Path]:
    out: List[pathlib.Path] = []
    for it in inputs:
        p = pathlib.Path(it)
        if p.is_dir():
            out.extend([x for x in p.rglob("*") if x.is_file()])
        elif p.is_file():
            out.append(p)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Resolve donor tier from files")
    ap.add_argument("paths", nargs="+", help="files or directories")
    ap.add_argument("--rules", default="donaterequire.txt", help="rules file path")
    args = ap.parse_args()

    rules = load_rules(pathlib.Path(args.rules))
    files = collect_files(args.paths)

    all_ids: Set[str] = set()
    for f in files:
        if f.suffix.lower() not in {".mldsl", ".json", ".txt", ".md"}:
            continue
        try:
            all_ids |= extract_ids(f.read_text(encoding="utf-8", errors="ignore"))
        except Exception:
            pass

    tier, matched = resolve_tier(all_ids, rules)
    print(f"tier={tier}")
    if matched:
        print("matched_ids=" + ",".join(sorted(matched, key=int)))
    else:
        print("matched_ids=none")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
