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


def _pick_java_home(version: int) -> str | None:
    # Common CI and local env vars first.
    keys = []
    if version == 21:
        keys.extend(("JAVA_HOME_21_X64", "JAVA_HOME_21", "JDK21_HOME"))
    if version == 17:
        keys.extend(("JAVA_HOME_17_X64", "JAVA_HOME_17", "JDK17_HOME"))
    keys.append("JAVA_HOME")
    for key in keys:
        value = os.environ.get(key, "").strip()
        if value and Path(value).exists() and (Path(value) / "bin" / "java.exe").exists():
            # Ensure requested major version when JAVA_HOME is generic.
            if key == "JAVA_HOME":
                try:
                    out = subprocess.check_output(
                        [str(Path(value) / "bin" / "java.exe"), "-version"],
                        stderr=subprocess.STDOUT,
                        text=True,
                    )
                    if f'"{version}.' not in out and f' {version} ' not in out:
                        continue
                except Exception:
                    continue
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
        for child in sorted(base.glob(f"**/*jdk-{version}*"), reverse=True):
            if child.is_dir() and (child / "bin" / "java.exe").exists():
                return str(child)
    return None


def build_fabric120(repo: Path, task: str) -> None:
    module = repo / "modern" / "fabric120"
    java17 = _pick_java_home(17)
    if not java17:
        raise SystemExit(
            "Fabric 1.20 build requires JDK 17+.\n"
            "Set JAVA_HOME_17_X64 (or JAVA_HOME_17) and rerun."
        )
    env = os.environ.copy()
    env["JAVA_HOME"] = java17
    env["PATH"] = str(Path(java17) / "bin") + os.pathsep + env.get("PATH", "")
    run([*gradle_cmd_for_module(module, repo), "-p", str(module), task], repo, env=env)

def build_fabric121(repo: Path, task: str) -> None:
    module = repo / "modern" / "fabric121"
    java21 = _pick_java_home(21)
    if not java21:
        raise SystemExit(
            "Fabric 1.21 build requires JDK 21.\n"
            "Set JAVA_HOME_21_X64 (or JAVA_HOME_21) and rerun."
        )
    env = os.environ.copy()
    env["JAVA_HOME"] = java21
    env["PATH"] = str(Path(java21) / "bin") + os.pathsep + env.get("PATH", "")
    run([*gradle_cmd_for_module(module, repo), "-p", str(module), task], repo, env=env)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build matrix for BetterCode multi-version targets")
    parser.add_argument(
        "target",
        choices=["legacy112", "fabric120", "fabric121", "all"],
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
    elif args.target == "fabric121":
        build_fabric121(repo, args.task)
    else:
        build_legacy112(repo, args.task)
        build_fabric120(repo, args.task)
        build_fabric121(repo, args.task)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
