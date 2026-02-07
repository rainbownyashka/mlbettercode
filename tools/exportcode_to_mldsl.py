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

COLOR_CODE_RE = re.compile(r"[§&][0-9A-FK-ORa-fk-or]")


def _q(s: str) -> str:
    s = s.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{s}"'


def _repair_mojibake(s: str) -> str:
    if not s:
        return ""
    # Heuristic: UTF-8 bytes decoded as CP1251/latin1 often look like 'Р...' spam
    # Conservative trigger only for classic mojibake sequences like "РџСЂРё..."
    if re.search(r"(?:Р.|С.){3,}", s):
        for enc in ("cp1251", "latin1"):
            try:
                fixed = s.encode(enc, errors="ignore").decode("utf-8", errors="ignore")
                if fixed.count("Р") + fixed.count("С") < s.count("Р") + s.count("С"):
                    return fixed
            except Exception:
                pass
    return s


def _clean_text(s: str) -> str:
    s = _repair_mojibake(str(s or ""))
    s = COLOR_CODE_RE.sub("", s)
    return s.strip()


def _sign(block: dict[str, Any]) -> tuple[str, str]:
    sign = block.get("sign")
    if not isinstance(sign, list):
        return "", ""
    s1 = _clean_text(sign[0]) if len(sign) > 0 else ""
    s2 = _clean_text(sign[1]) if len(sign) > 1 else ""
    return s1, s2


def _enum_selected(slot: dict[str, Any]) -> tuple[int | None, str]:
    lore = slot.get("lore")
    if not isinstance(lore, list):
        return None, ""
    options: list[str] = []
    selected = None
    for line in lore:
        t = str(line or "")
        if ("●" not in t and "○" not in t and "\u25cf" not in t and "\u25cb" not in t):
            continue
        clean = _clean_text(t.replace("●", "").replace("○", "").replace("\u25cf", "").replace("\u25cb", ""))
        options.append(clean)
        if "●" in t or "\u25cf" in t:
            selected = len(options) - 1
    if selected is None:
        return None, ""
    text = options[selected] if 0 <= selected < len(options) else ""
    return max(0, selected), text


def _infer_slot_value(slot: dict[str, Any]) -> str:
    reg = str(slot.get("registry") or slot.get("id") or "").lower()
    disp = _clean_text(slot.get("displayClean") or slot.get("display") or slot.get("displayName") or "")
    nbt = str(slot.get("nbt") or "")
    count = int(slot.get("count") or 1)

    if reg in ("minecraft:book", "minecraft:writable_book", "minecraft:written_book"):
        return f"text({_q(disp or 'text')})"
    if reg == "minecraft:slime_ball":
        num = re.sub(r"[^0-9\.-]", "", disp)
        return f"num({num or '0'})"
    if reg == "minecraft:magma_cream":
        return f"var({_q(disp or 'varname')})"
    if reg == "minecraft:item_frame":
        return f"arr({_q(disp or 'arr')})"
    if reg == "minecraft:paper":
        return f"loc({_q(disp or 'loc')})"
    if reg == "minecraft:apple":
        m = re.search(r'locname\\":\\"([^\\\"]+)', nbt)
        gv = _clean_text(m.group(1)) if m else (disp or "gameval")
        return f"apple({_q(gv)})"

    parts = [f"{_q(reg or 'minecraft:stone')}"]
    if count != 1:
        parts.append(f"count={count}")
    if disp:
        parts.append(f"name={_q(disp)}")
    if nbt and nbt != "{}":
        parts.append(f"nbt={_q(nbt)}")
    return f"item({', '.join(parts)})"


def _slots(chest: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not isinstance(chest, dict):
        return []
    slots = chest.get("slots")
    if isinstance(slots, list):
        return [s for s in slots if isinstance(s, dict)]
    return []


def _block_pos(block: dict[str, Any]) -> tuple[int, int, int] | None:
    pos = block.get("pos")
    if not isinstance(pos, dict):
        return None
    try:
        return int(pos.get("x")), int(pos.get("y")), int(pos.get("z"))
    except Exception:
        return None


def _has_data_block(block: dict[str, Any]) -> bool:
    bid = str(block.get("block", ""))
    if bid in ("minecraft:piston", "minecraft:sticky_piston"):
        return False
    if bid and bid != "minecraft:air":
        return True
    sign = block.get("sign")
    if isinstance(sign, list):
        for s in sign:
            if str(s or "").strip():
                return True
    chest = block.get("chest")
    if isinstance(chest, dict):
        return True
    return False


def _normalize_row_blocks(row: dict[str, Any], blocks: list[dict[str, Any]]) -> list[dict[str, Any]]:
    # Build deterministic step order by row geometry:
    # entry at (glass.x - 2*p), optional side piston at (entry.x + 1).
    glass = row.get("glass")
    if not isinstance(glass, dict):
        return blocks
    try:
        gx = int(glass.get("x"))
        gy = int(glass.get("y"))
        gz = int(glass.get("z"))
    except Exception:
        return blocks
    main_y = gy + 1

    by_pos: dict[tuple[int, int, int], list[dict[str, Any]]] = {}
    for b in blocks:
        if not isinstance(b, dict):
            continue
        p = _block_pos(b)
        if p is None:
            continue
        by_pos.setdefault(p, []).append(b)

    out: list[dict[str, Any]] = []
    consumed: set[int] = set()
    # hard upper bound, exporter uses 256 max steps
    for p in range(0, 512):
        ex = gx - 2 * p
        entry_pos = (ex, main_y, gz)
        side_pos = (ex + 1, main_y, gz)
        entry_candidates = by_pos.get(entry_pos, [])
        side_candidates = by_pos.get(side_pos, [])

        entry = None
        for c in entry_candidates:
            if id(c) in consumed:
                continue
            # Entry should not be piston if normal entry exists.
            if str(c.get("block", "")).endswith("piston"):
                continue
            entry = c
            break
        if entry is None:
            for c in entry_candidates:
                if id(c) in consumed:
                    continue
                entry = c
                break

        side_piston = None
        for c in side_candidates:
            if id(c) in consumed:
                continue
            bid = str(c.get("block", ""))
            if bid in ("minecraft:piston", "minecraft:sticky_piston"):
                side_piston = c
                break

        had_any = False
        if entry is not None and _has_data_block(entry):
            out.append(entry)
            consumed.add(id(entry))
            had_any = True
        if side_piston is not None:
            # Side piston near condition blocks is often geometry noise for converter;
            # real close token should come from subsequent action step.
            entry_bid = str((entry or {}).get("block", ""))
            if entry_bid in CONDITION_BLOCKS:
                side_piston = None
        if side_piston is not None:
            out.append(side_piston)
            consumed.add(id(side_piston))
            had_any = True

        if not had_any and p > 4:
            # stop on long tail of empties
            # (real exporter stops earlier by empty-pairs)
            # this keeps normalizer bounded and stable
            tail_ok = True
            for k in range(1, 5):
                tx = gx - 2 * (p - k)
                if by_pos.get((tx, main_y, gz)) or by_pos.get((tx + 1, main_y, gz)):
                    tail_ok = False
                    break
            if tail_ok:
                break

    # Append unconsumed meaningful blocks in original order as fallback.
    for b in blocks:
        if not isinstance(b, dict):
            continue
        if id(b) in consumed:
            continue
        if _has_data_block(b):
            out.append(b)
            consumed.add(id(b))
    return out


def _slot_by_no(slots: list[dict[str, Any]], no: int) -> dict[str, Any] | None:
    for s in slots:
        try:
            if int(s.get("slot")) == no:
                return s
        except Exception:
            pass
    return None


def _first_non_pane_slot(slots: list[dict[str, Any]]) -> dict[str, Any] | None:
    for s in slots:
        reg = str(s.get("registry", "")).lower()
        if reg.endswith("stained_glass_pane"):
            continue
        return s
    return None


def _call_from_known(sign1: str, sign2: str, slots: list[dict[str, Any]], module_hint: str | None) -> str | None:
    s2 = sign2.lower()

    if "поставить слот" in s2:
        s = _slot_by_no(slots, 13) or _first_non_pane_slot(slots)
        num = "0"
        if s:
            m = re.search(r"-?\d+(?:\.\d+)?", _clean_text(s.get("displayClean") or s.get("display") or s.get("displayName") or ""))
            if m:
                num = m.group(0)
        return f"player.поставить_слот(num={num})"

    if "режим игры" in s2:
        s = _slot_by_no(slots, 13) or _first_non_pane_slot(slots)
        if s:
            _, txt = _enum_selected(s)
            if txt:
                return f"player.режим_игры(режим_игры={_q(txt)})"
        return "player.режим_игры()"

    if "имеет право" in s2:
        s = _slot_by_no(slots, 13) or _first_non_pane_slot(slots)
        if s:
            _, txt = _enum_selected(s)
            if txt:
                return f"if_player.имеет_право(право_для_проверки={_q(txt)})"
        return "if_player.имеет_право()"

    if "сообщение" in s2:
        s = _slot_by_no(slots, 9) or _first_non_pane_slot(slots)
        if s:
            val = _infer_slot_value(s)
            if val.startswith("text("):
                return f"player.сообщение({val})"
        return "player.сообщение()"

    if "держит" in s2:
        s = _first_non_pane_slot(slots)
        if s:
            return f"if_player.держит(item={_infer_slot_value(s)})"
        return "if_player.держит()"

    return None


def _generic_call(bid: str, sign2: str, slots: list[dict[str, Any]]) -> str:
    module = BLOCK_MODULE.get(bid, "misc")
    fn = sign2.replace(" ", "_") if sign2 else "unnamed"
    args: list[str] = []
    for s in slots:
        try:
            slot_no = int(s.get("slot"))
        except Exception:
            continue
        args.append(f"slotraw({slot_no})={_infer_slot_value(s)}")
        idx, _ = _enum_selected(s)
        if idx is not None and idx > 0:
            args.append(f"clicks({slot_no},{idx})=0")
    if args:
        return f"{module}.{fn}({', '.join(args)})"
    return f"{module}.{fn}()"


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
        blocks = _normalize_row_blocks(row, [b for b in blocks if isinstance(b, dict)])
        if not blocks:
            continue

        indent = 0
        first = blocks[0] if isinstance(blocks[0], dict) else {}
        b0 = str(first.get("block", ""))
        s1_0, s2_0 = _sign(first)

        if b0 in ("minecraft:diamond_block", "minecraft:gold_block"):
            lines.append(f"event({_q(s2_0 or s1_0 or 'event')}) {{")
            indent = 1
            iter_blocks = blocks[1:]
        elif b0 == "minecraft:lapis_block":
            lines.append(f"func({_q(s2_0 or s1_0 or 'func')}) {{")
            indent = 1
            iter_blocks = blocks[1:]
        elif b0 == "minecraft:emerald_block":
            ticks = "20"
            sign = first.get("sign")
            if isinstance(sign, list) and len(sign) >= 3 and str(sign[2]).strip():
                ticks = str(sign[2]).strip()
            lines.append(f"loop({_q(s2_0 or s1_0 or 'loop')}, {ticks}) {{")
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
                continue

            sign1, sign2 = _sign(block)
            slots = _slots(block.get("chest"))

            if bid in CONDITION_BLOCKS:
                known = _call_from_known(sign1, sign2, slots, "if_player")
                if known:
                    lines.append("    " * indent + known + " {")
                else:
                    lines.append("    " * indent + f"if_raw({_q(sign2 or sign1 or 'condition')}) {{")
                indent += 1
                continue

            if bid == "minecraft:end_stone":
                if indent > 0:
                    indent -= 1
                    lines.append("    " * indent + "} else {")
                    indent += 1
                continue

            known = _call_from_known(sign1, sign2, slots, BLOCK_MODULE.get(bid))
            if known:
                lines.append("    " * indent + known)
            else:
                lines.append("    " * indent + _generic_call(bid, sign2 or sign1, slots))

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
