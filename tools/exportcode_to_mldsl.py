# -*- coding: utf-8 -*-
#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

BLOCK_MODULE = {
    "minecraft:cobblestone": "player",
    "minecraft:nether_brick": "game",
    "minecraft:iron_block": "var",
    "minecraft:bookshelf": "array",
    "minecraft:purpur_block": "select",
    "minecraft:lapis_ore": "game",
}

CONDITION_BLOCKS = {
    "minecraft:planks",
    "minecraft:red_nether_brick",
    "minecraft:brick_block",
    "minecraft:obsidian",
}


def _q(s: str) -> str:
    s = s.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{s}"'


def _sign_name(block: dict[str, Any]) -> str:
    sign = block.get("sign")
    if isinstance(sign, list):
        parts = [str(x).strip() for x in sign if str(x).strip()]
        if len(parts) >= 2:
            return parts[1]
        if parts:
            return parts[0]
    return ""


def _infer_value(slot: dict[str, Any]) -> str:
    reg = str(slot.get("registry", "")).lower()
    disp = str(slot.get("displayClean") or slot.get("display") or "").strip()
    lore = slot.get("lore") or []
    lore_join = "\n".join(str(x) for x in lore)
    nbt = str(slot.get("nbt") or "")
    count = int(slot.get("count") or 1)

    if reg == "minecraft:book":
        return f"text({_q(disp or 'text')})"
    if reg == "minecraft:slime_ball":
        num = re.sub(r"[^0-9\.-]", "", disp)
        return f"num({num or '0'})"
    if reg == "minecraft:magma_cream":
        name = disp or "varname"
        if "\u0421\u041e\u0425\u0420\u0410\u041d" in lore_join.upper() or "SAVE" in lore_join.upper():
            return f"var_save({_q(name)})"
        return f"var({_q(name)})"
    if reg == "minecraft:item_frame":
        return f"arr({_q(disp or 'arr')})"
    if reg == "minecraft:paper":
        return f"loc({_q(disp or 'loc')})"
    if reg == "minecraft:apple":
        m = re.search(r"locname\\\":\\\"([^\\\"]+)\\\"", nbt)
        gv = m.group(1) if m else (disp or "gameval")
        return f"apple({_q(gv)})"

    item_parts = [f"{_q(reg or 'minecraft:stone')}"]
    if count != 1:
        item_parts.append(f"count={count}")
    if disp:
        item_parts.append(f"name={_q(disp)}")
    if nbt and nbt != "{}":
        item_parts.append(f"nbt={_q(nbt)}")
    return f"item({', '.join(item_parts)})"


def _enum_clicks(slot: dict[str, Any]) -> int | None:
    lore = slot.get("lore")
    if not isinstance(lore, list) or not lore:
        return None

    options: list[int] = []
    selected: int | None = None
    for i, line in enumerate(lore):
        t = str(line)
        has_option = ("\u25cf" in t) or ("\u25cb" in t) or ("●" in t) or ("○" in t)
        if not has_option:
            continue
        options.append(i)
        if ("\u25cf" in t) or ("●" in t):
            selected = len(options) - 1

    if not options or selected is None:
        return None
    return max(0, selected)


def _args_from_chest(block: dict[str, Any]) -> list[str]:
    chest = block.get("chest")
    if not isinstance(chest, dict):
        return []
    slots = chest.get("slots")
    if not isinstance(slots, list):
        return []

    out: list[str] = []
    for s in slots:
        if not isinstance(s, dict):
            continue
        try:
            slot_no = int(s.get("slot"))
        except Exception:
            continue

        value = _infer_value(s)
        out.append(f"slotraw({slot_no})={value}")

        clicks = _enum_clicks(s)
        if clicks is not None and clicks > 0:
            out.append(f"clicks({slot_no},{clicks})=0")
    return out


def convert_export_to_mldsl(data: dict[str, Any]) -> str:
    rows = data.get("rows")
    if not isinstance(rows, list):
        raise ValueError("Invalid exportcode: rows[] missing")

    lines: list[str] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        blocks = row.get("blocks")
        if not isinstance(blocks, list) or not blocks:
            continue

        indent = 0
        first = blocks[0] if isinstance(blocks[0], dict) else {}
        b0 = str(first.get("block", ""))
        name0 = _sign_name(first)

        if b0 in ("minecraft:diamond_block", "minecraft:gold_block"):
            lines.append(f"event({_q(name0 or 'event')}) {{")
            indent = 1
            iter_blocks = blocks[1:]
        elif b0 == "minecraft:lapis_block":
            lines.append(f"func({_q(name0 or 'func')}) {{")
            indent = 1
            iter_blocks = blocks[1:]
        elif b0 == "minecraft:emerald_block":
            ticks = "20"
            sign = first.get("sign")
            if isinstance(sign, list) and len(sign) >= 3 and str(sign[2]).strip():
                ticks = str(sign[2]).strip()
            lines.append(f"loop({_q(name0 or 'loop')}, {ticks}) {{")
            indent = 1
            iter_blocks = blocks[1:]
        else:
            lines.append(f"# row {row.get('row', '?')}")
            iter_blocks = blocks

        for block in iter_blocks:
            if not isinstance(block, dict):
                continue
            bid = str(block.get("block", ""))

            if bid in ("minecraft:piston", "minecraft:sticky_piston"):
                if indent > 0:
                    indent -= 1
                    lines.append("    " * indent + "}")
                else:
                    lines.append("# unmatched piston")
                continue

            sign_name = _sign_name(block)
            args = _args_from_chest(block)
            arg_text = ", ".join(args)

            if bid in CONDITION_BLOCKS:
                cond = sign_name or "condition"
                lines.append("    " * indent + f"if_raw({_q(cond)}) {{")
                indent += 1
                continue

            if bid == "minecraft:end_stone":
                if indent > 0:
                    indent -= 1
                    lines.append("    " * indent + "} else {")
                    indent += 1
                else:
                    lines.append("# else without open if")
                continue

            module = BLOCK_MODULE.get(bid)
            if module:
                fn = sign_name or "unnamed"
                if arg_text:
                    lines.append("    " * indent + f"{module}.{fn}({arg_text})")
                else:
                    lines.append("    " * indent + f"{module}.{fn}()")
            else:
                if arg_text:
                    lines.append("    " * indent + f"# unsupported {bid}: {sign_name} ({arg_text})")
                else:
                    lines.append("    " * indent + f"# unsupported {bid}: {sign_name}")

        while indent > 0:
            indent -= 1
            lines.append("    " * indent + "}")
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert exportcode_*.json into MLDSL draft")
    parser.add_argument("input", help="Path to exportcode_*.json")
    parser.add_argument("-o", "--output", help="Output .mldsl path")
    args = parser.parse_args()

    in_path = Path(args.input)
    if not in_path.exists():
        raise SystemExit(f"Input not found: {in_path}")

    raw = in_path.read_text(encoding="utf-8-sig")
    data = json.loads(raw)
    out_text = convert_export_to_mldsl(data)

    out_path = Path(args.output) if args.output else in_path.with_suffix(".mldsl")
    out_path.write_text(out_text, encoding="utf-8")
    print(f"OK: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
