#!/usr/bin/env python3
"""Docs scope guard for MLBetterCode mod repo."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"

ALLOW_CROSS = {"CROSS_PROJECT_INDEX.md"}
FORBIDDEN = [
    r"firebase",
    r"supabase",
    r"neon",
]

bad = []
for p in DOCS.rglob("*.md"):
    if p.name in ALLOW_CROSS:
        continue
    txt = p.read_text(encoding="utf-8", errors="ignore").lower()
    for pat in FORBIDDEN:
        if re.search(pat, txt):
            bad.append((p, pat))

if bad:
    print("Docs scope violations:")
    for p, pat in bad:
        print(f"- {p.relative_to(ROOT)}: matches '{pat}'")
    sys.exit(1)

print("Docs scope check passed (mod repo).")
