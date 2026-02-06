from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


def run(cmd: list[str], cwd: Path, env: dict[str, str] | None = None) -> None:
    print("+", " ".join(cmd), f"(cwd={cwd})")
    subprocess.check_call(cmd, cwd=str(cwd), env=env)


def gradle_cmd(repo: Path) -> list[str]:
    if sys.platform.startswith("win"):
        return [str(repo / "gradlew.bat")]
    return [str(repo / "gradlew")]

def gradle_cmd_for_module(module_dir: Path, repo: Path) -> list[str]:
    if sys.platform.startswith("win"):
        candidate = module_dir / "gradlew.bat"
    else:
        candidate = module_dir / "gradlew"
    if candidate.exists():
        return [str(candidate)]
    return gradle_cmd(repo)


def build_legacy112(repo: Path, task: str) -> None:
    run([*gradle_cmd(repo), task], repo)


def _pick_java17_home() -> str | None:
    # Common CI and local env vars first.
    for key in ("JAVA_HOME_17_X64", "JAVA_HOME_17", "JDK17_HOME"):
        value = os.environ.get(key, "").strip()
        if value and Path(value).exists():
            return value

    # Typical Windows install locations.
    candidates = [
        Path(r"C:\Program Files\Eclipse Adoptium"),
        Path(r"C:\Program Files\Java"),
        Path(r"C:\Program Files\Microsoft"),
    ]
    for base in candidates:
        if not base.exists():
            continue
        for child in sorted(base.glob("**/*jdk-17*"), reverse=True):
            if child.is_dir() and (child / "bin" / "java.exe").exists():
                return str(child)
    return None


def build_fabric120(repo: Path, task: str) -> None:
    module = repo / "modern" / "fabric120"
    java17 = _pick_java17_home()
    if not java17:
        raise SystemExit(
            "Fabric 1.20 build requires JDK 17+.\n"
            "Set JAVA_HOME_17_X64 (or JAVA_HOME_17) and rerun."
        )
    env = os.environ.copy()
    env["JAVA_HOME"] = java17
    env["PATH"] = str(Path(java17) / "bin") + os.pathsep + env.get("PATH", "")
    run([*gradle_cmd_for_module(module, repo), "-p", str(module), task], repo, env=env)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build matrix for BetterCode multi-version targets")
    parser.add_argument(
        "target",
        choices=["legacy112", "fabric120", "all"],
        help="Build target profile",
    )
    parser.add_argument(
        "--task",
        default="build",
        help="Gradle task (default: build)",
    )
    args = parser.parse_args()

    repo = Path(__file__).resolve().parents[1]
    if args.target == "legacy112":
        build_legacy112(repo, args.task)
    elif args.target == "fabric120":
        build_fabric120(repo, args.task)
    else:
        build_legacy112(repo, args.task)
        build_fabric120(repo, args.task)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
