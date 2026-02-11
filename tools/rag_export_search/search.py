#!/usr/bin/env python3
"""
RAG-like search for regallactions export files.
Uses Ollama embeddings for semantic ranking + lightweight lexical score.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


# TAG:RAG_PARSE_RECORDS - Parses records from regallactions_export.txt format.
@dataclass
class Record:
    index: int
    path: str
    category: str
    subitem: str
    gui: str
    sign1: str
    sign2: str
    sign3: str
    sign4: str
    has_chest: str
    items: list[str]

    def title(self) -> str:
        text = self.subitem or self.category or self.gui
        text = strip_minecraft_codes(text)
        if "|" in text:
            text = text.split("|", 1)[0]
        return text.strip()

    def body(self) -> str:
        parts = [
            self.category,
            self.subitem,
            self.gui,
            self.sign1,
            self.sign2,
            self.sign3,
            self.sign4,
            " ".join(self.items[:20]),
        ]
        return "\n".join(strip_minecraft_codes(p) for p in parts if p)


def strip_minecraft_codes(text: str) -> str:
    if not text:
        return ""
    return re.sub(r"§.", "", text).replace("\\n", " ").strip()


def parse_export(path: Path) -> list[Record]:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    out: list[Record] = []
    cur: dict[str, object] | None = None

    def flush() -> None:
        nonlocal cur
        if not cur:
            return
        out.append(
            Record(
                index=len(out) + 1,
                path=str(cur.get("path", "")),
                category=str(cur.get("category", "")),
                subitem=str(cur.get("subitem", "")),
                gui=str(cur.get("gui", "")),
                sign1=str(cur.get("sign1", "")),
                sign2=str(cur.get("sign2", "")),
                sign3=str(cur.get("sign3", "")),
                sign4=str(cur.get("sign4", "")),
                has_chest=str(cur.get("hasChest", "false")),
                items=list(cur.get("items", [])),
            )
        )
        cur = None

    for line in lines:
        if line.startswith("# record"):
            flush()
            cur = {"items": []}
            continue
        if line.startswith("records="):
            continue
        if cur is None or not line or "=" not in line:
            continue
        k, v = line.split("=", 1)
        if k == "item":
            cur["items"].append(v)
        else:
            cur[k] = v

    flush()
    return out


def tokenize(text: str) -> set[str]:
    return set(re.findall(r"[\wа-яА-ЯёЁ]+", text.lower()))


def lexical_score(query: str, text: str) -> float:
    q = tokenize(query)
    t = tokenize(text)
    if not q or not t:
        return 0.0
    common = len(q & t)
    return common / max(1, len(q))


# TAG:RAG_OLLAMA_EMBED - Requests embeddings from local Ollama endpoint.
def _truncate_for_embed(text: str, max_chars: int = 2800) -> str:
    # TAG:RAG_EMBED_TRUNCATE - Prevents Ollama 500 on very long export records.
    if len(text) <= max_chars:
        return text
    return text[:max_chars]


def ollama_embed(host: str, model: str, text: str) -> list[float]:
    text = _truncate_for_embed(text)
    # Prefer old endpoint first, then fallback to /api/embed.
    candidates = [
        (f"{host.rstrip('/')}/api/embeddings", {"model": model, "prompt": text}, "embedding"),
        (f"{host.rstrip('/')}/api/embed", {"model": model, "input": text}, "embeddings"),
    ]
    last_err: Exception | None = None
    for url, payload_obj, field in candidates:
        try:
            payload = json.dumps(payload_obj).encode("utf-8")
            req = urllib.request.Request(
                url=url,
                data=payload,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
            data = json.loads(raw)
            if field == "embedding":
                emb = data.get("embedding")
            else:
                embs = data.get("embeddings")
                emb = embs[0] if isinstance(embs, list) and embs else None
            if isinstance(emb, list) and emb:
                return [float(x) for x in emb]
            raise RuntimeError(f"Ollama response has no {field}")
        except Exception as e:
            last_err = e
            continue
    raise RuntimeError(f"Ollama embed failed: {last_err}")


def ollama_list_models(host: str) -> list[str]:
    req = urllib.request.Request(
        url=f"{host.rstrip('/')}/api/tags",
        headers={"Content-Type": "application/json"},
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        raw = resp.read().decode("utf-8", errors="replace")
    data = json.loads(raw)
    models = data.get("models") or []
    out: list[str] = []
    for m in models:
        name = m.get("name")
        if isinstance(name, str) and name.strip():
            out.append(name.strip())
    return out


def cosine(a: Iterable[float], b: Iterable[float]) -> float:
    a = list(a)
    b = list(b)
    if len(a) != len(b) or not a:
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


def load_index(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def save_index(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")


def build_index_data(
    records: list[Record],
    export_path: Path,
    host: str,
    model: str,
) -> dict:
    # TAG:RAG_BUILD_INDEX - Builds a simple local vector index for faster repeated searches.
    started = time.time()
    rows = []
    for rec in records:
        body = rec.body()
        rows.append(
            {
                "index": rec.index,
                "path": rec.path,
                "title": rec.title(),
                "body": body,
                "embedding": ollama_embed(host, model, body),
            }
        )
    return {
        "version": 1,
        "created_at": int(time.time()),
        "build_seconds": round(time.time() - started, 3),
        "model": model,
        "host": host,
        "export_path": str(export_path),
        "export_mtime": int(export_path.stat().st_mtime),
        "records": rows,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="Semantic search over regallactions export using Ollama")
    ap.add_argument("export", help="Path to regallactions_export.txt")
    ap.add_argument("query", help="Search query in natural language")
    ap.add_argument("--top", type=int, default=10, help="Top results count")
    ap.add_argument("--model", default="nomic-embed-text", help="Ollama embedding model")
    ap.add_argument("--host", default="http://127.0.0.1:11434", help="Ollama host")
    ap.add_argument("--no-ollama", action="store_true", help="Disable semantic scoring")
    ap.add_argument("--index", default="", help="Path to local vector index (.json)")
    ap.add_argument("--build-index", action="store_true", help="Build/update index and exit")
    args = ap.parse_args()

    export_path = Path(args.export)
    if not export_path.is_file():
        print(f"error: file not found: {export_path}", file=sys.stderr)
        return 2

    records = parse_export(export_path)
    if not records:
        print("error: no records parsed", file=sys.stderr)
        return 3

    use_ollama = not args.no_ollama
    query_emb: list[float] | None = None
    doc_embs: list[list[float] | None] = [None] * len(records)
    model_used = args.model
    index_data: dict | None = None

    if args.index:
        idx_path = Path(args.index)
        if idx_path.is_file():
            try:
                index_data = load_index(idx_path)
            except Exception as e:
                print(f"warn: failed to load index: {e}", file=sys.stderr)
                index_data = None

        if args.build_index or index_data is None:
            try:
                index_data = build_index_data(records, export_path, args.host, model_used)
                save_index(idx_path, index_data)
                print(f"index_saved={idx_path} records={len(index_data.get('records', []))} model={model_used}")
            except Exception as e:
                print(f"warn: index build failed: {e}", file=sys.stderr)
                index_data = None
            if args.build_index:
                return 0

    if use_ollama:
        try:
            query_emb = ollama_embed(args.host, model_used, args.query)
            if index_data and isinstance(index_data.get("records"), list):
                idx_rows = index_data["records"]
                if len(idx_rows) == len(records):
                    for i, row in enumerate(idx_rows):
                        emb = row.get("embedding")
                        if isinstance(emb, list) and emb:
                            doc_embs[i] = [float(x) for x in emb]
                else:
                    for i, rec in enumerate(records):
                        doc_embs[i] = ollama_embed(args.host, model_used, rec.body())
            else:
                for i, rec in enumerate(records):
                    doc_embs[i] = ollama_embed(args.host, model_used, rec.body())
        except (urllib.error.URLError, TimeoutError, RuntimeError, json.JSONDecodeError) as e:
            # TAG:RAG_OLLAMA_MODEL_FALLBACK - Tries installed embedding models before disabling semantic scoring.
            fallback_models: list[str] = []
            try:
                installed = ollama_list_models(args.host)
                preferred = [
                    "nomic-embed-text",
                    "mxbai-embed-large",
                    "snowflake-arctic-embed",
                    "bge-m3",
                ]
                for p in preferred:
                    for name in installed:
                        if name.startswith(p):
                            fallback_models.append(name)
                if args.model not in fallback_models and args.model in installed:
                    fallback_models.insert(0, args.model)
            except Exception:
                fallback_models = []

            switched = False
            for m in fallback_models:
                if m == model_used:
                    continue
                try:
                    query_emb = ollama_embed(args.host, m, args.query)
                    for i, rec in enumerate(records):
                        doc_embs[i] = ollama_embed(args.host, m, rec.body())
                    model_used = m
                    switched = True
                    break
                except Exception:
                    continue

            if not switched:
                print(
                    "warn: ollama disabled ({0}). Try: `ollama pull nomic-embed-text` "
                    "or pass --model with an installed embedding model.".format(e),
                    file=sys.stderr,
                )
                use_ollama = False

    scored: list[tuple[float, float, float, Record]] = []
    for i, rec in enumerate(records):
        lex = lexical_score(args.query, rec.body())
        sem = cosine(query_emb, doc_embs[i]) if use_ollama and query_emb and doc_embs[i] else 0.0
        score = (0.35 * lex) + (0.65 * sem if use_ollama else 0.0)
        scored.append((score, sem, lex, rec))

    scored.sort(key=lambda x: x[0], reverse=True)
    print(f"query={args.query}")
    print(f"records={len(records)} ollama={'on' if use_ollama else 'off'} model={model_used}")
    print("")

    for rank, (score, sem, lex, rec) in enumerate(scored[: args.top], 1):
        print(f"{rank:02d}. score={score:.4f} sem={sem:.4f} lex={lex:.4f} rec={rec.index} path={rec.path or '-'}")
        print(f"    {rec.title()}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
