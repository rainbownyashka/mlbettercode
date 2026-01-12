#!/usr/bin/env python3
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TRIGGER = ROOT / "build.trigger"
LOG = ROOT / "build.log"
STATUS = ROOT / "build.status"


def write_status(text: str) -> None:
    STATUS.write_text(text.strip() + "\n", encoding="utf-8")


def run_build() -> int:
    with LOG.open("w", encoding="utf-8") as out:
        out.write("== build started ==\n")
        out.flush()
        proc = subprocess.Popen(["./gradlew", "build"], cwd=str(ROOT), stdout=out, stderr=subprocess.STDOUT)
        return proc.wait()


def main() -> None:
    write_status("idle")
    last_mtime = None
    while True:
        try:
            if TRIGGER.exists():
                mtime = TRIGGER.stat().st_mtime
                if last_mtime is None or mtime != last_mtime:
                    last_mtime = mtime
                    content = TRIGGER.read_text(encoding="utf-8").strip().lower()
                    if content == "build":
                        write_status("running")
                        code = run_build()
                        write_status(f"done:{code}")
                        TRIGGER.write_text("", encoding="utf-8")
            time.sleep(1.0)
        except KeyboardInterrupt:
            write_status("stopped")
            break
        except Exception as exc:
            write_status(f"error:{exc}")
            time.sleep(2.0)


if __name__ == "__main__":
    main()
