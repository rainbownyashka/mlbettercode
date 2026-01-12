from pathlib import Path

SRC = Path(r"src/main/java/com/example/examplemod/ExampleMod.java")
DST = Path(r"src/main/java/com/example/examplemod/ExampleMod.javaai")

def main():
    if not SRC.exists():
        raise SystemExit(f"Source not found: {SRC}")

    lines = SRC.read_text(encoding="utf-8", errors="replace").splitlines(True)

    width = len(str(len(lines)))  # number width based on line count

    out = []
    for i, line in enumerate(lines, start=1):
        # Keep original line endings; prefix with fixed-width number
        out.append(f"{i:>{width}}: {line}")

    DST.write_text("".join(out), encoding="utf-8", newline="")

    print(f"OK\nWrote: {DST}\nLines: {len(lines)}\nNumber width: {width}")

if __name__ == "__main__":
    main()
