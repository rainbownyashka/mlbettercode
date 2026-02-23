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
    java8 = _pick_java_home(8)
    if not java8:
        raise SystemExit(
            "Legacy 1.12 build requires JDK 8.\n"
            "Set JAVA_HOME_8_X64 (or JAVA_HOME_8) and rerun."
        )
    env = os.environ.copy()
    env["JAVA_HOME"] = java8
    env["PATH"] = str(Path(java8) / "bin") + os.pathsep + env.get("PATH", "")
    run([*gradle_cmd(repo), task], repo, env=env)

def _java_bin(home: Path) -> Path:
    if sys.platform.startswith("win"):
        return home / "bin" / "java.exe"
    return home / "bin" / "java"


def _java_matches_major(home: Path, version: int) -> bool:
    java = _java_bin(home)
    if not java.exists():
        return False
    try:
        out = subprocess.check_output([str(java), "-version"], stderr=subprocess.STDOUT, text=True)
    except Exception:
        return False
    # Works for common outputs:
    #  - openjdk version "17.0.12"
    #  - openjdk 21.0.4 ...
    return (f'"{version}.' in out) or (f"openjdk {version}" in out) or (f" version \"{version}" in out)


def _pick_java_home(version: int) -> str | None:
    # Common CI and local env vars first.
    keys = []
    if version == 8:
        keys.extend(("JAVA_HOME_8_X64", "JAVA_HOME_8", "JDK8_HOME"))
    if version == 21:
        keys.extend(("JAVA_HOME_21_X64", "JAVA_HOME_21", "JDK21_HOME"))
    if version == 17:
        keys.extend(("JAVA_HOME_17_X64", "JAVA_HOME_17", "JDK17_HOME"))
    keys.append("JAVA_HOME")
    for key in keys:
        value = os.environ.get(key, "").strip()
        if not value:
            continue
        home = Path(value)
        if not home.exists():
            continue
        if key == "JAVA_HOME":
            if _java_matches_major(home, version):
                return value
            continue
        if _java_bin(home).exists():
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
            if child.is_dir() and _java_bin(child).exists():
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

def build_fabric1165(repo: Path, task: str) -> None:
    module = repo / "modern" / "fabric1165"
    java17 = _pick_java_home(17)
    if not java17:
        raise SystemExit(
            "Fabric 1.16.5 build uses Loom and requires JDK 17 runtime.\n"
            "Set JAVA_HOME_17_X64 (or JAVA_HOME_17) and rerun."
        )
    env = os.environ.copy()
    env["JAVA_HOME"] = java17
    env["PATH"] = str(Path(java17) / "bin") + os.pathsep + env.get("PATH", "")
    run([*gradle_cmd_for_module(module, repo), "-p", str(module), task], repo, env=env)

def build_forge1165(repo: Path, task: str) -> None:
    module = repo / "modern" / "forge1165"
    java17 = _pick_java_home(17)
    if not java17:
        raise SystemExit(
            "Forge 1.16.5 bootstrap build requires JDK 17 runtime.\n"
            "Set JAVA_HOME_17_X64 (or JAVA_HOME_17) and rerun."
        )
    env = os.environ.copy()
    env["JAVA_HOME"] = java17
    env["PATH"] = str(Path(java17) / "bin") + os.pathsep + env.get("PATH", "")
    run([*gradle_cmd_for_module(module, repo), "-p", str(module), task], repo, env=env)


def _legacy_export_source_path() -> Path | None:
    appdata = os.environ.get("APPDATA", "").strip()
    if not appdata:
        return None
    return Path(appdata) / ".minecraft" / "regallactions_export.txt"


def sync_legacy_tablesexport_snapshot(repo: Path) -> None:
    src = _legacy_export_source_path()
    if src is None:
        print("[build-matrix] legacy tablesexport sync skipped: APPDATA is not set")
        return
    if not src.exists():
        print(f"[build-matrix] legacy tablesexport sync skipped: source not found: {src}")
        return

    tool = repo / "tools" / "legacy_regallactions_to_tablesexport.py"
    if not tool.exists():
        print(f"[build-matrix] legacy tablesexport sync skipped: tool not found: {tool}")
        return

    out_repo = repo / "agentslock" / "tablesexports" / "1.12.2" / "tablesexport.from_legacy.names.txt"
    out_mc = Path(src.parent) / "mldsl_tables" / "1.12.2" / "tablesexport.from_legacy.names.txt"
    cmd_base = [sys.executable, str(tool), "--in", str(src), "--item-id-mode", "none"]

    try:
        run([*cmd_base, "--out", str(out_repo)], repo)
        run([*cmd_base, "--out", str(out_mc)], repo)
        print("[build-matrix] legacy tablesexport sync ok")
        print(f"[build-matrix] legacy tablesexport repo={out_repo}")
        print(f"[build-matrix] legacy tablesexport mc={out_mc}")
    except Exception as e:
        # Best-effort sync must not fail build targets.
        print(f"[build-matrix] legacy tablesexport sync failed: {e.__class__.__name__}: {e}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Build matrix for BetterCode multi-version targets")
    parser.add_argument(
        "target",
        choices=["legacy112", "fabric1165", "fabric120", "fabric121", "forge1165", "modern_all", "all"],
        help="Build target profile",
    )
    parser.add_argument(
        "--task",
        default="build",
        help="Gradle task (default: build)",
    )
    parser.add_argument(
        "--no-legacy-tables-sync",
        action="store_true",
        help="Disable best-effort sync of legacy 1.12.2 export into name-based tablesexport snapshots.",
    )
    args = parser.parse_args()

    repo = Path(__file__).resolve().parents[1]
    run_legacy_sync = not args.no_legacy_tables_sync and args.target in {
        "fabric1165",
        "fabric120",
        "fabric121",
        "forge1165",
        "modern_all",
        "all",
    }

    if args.target == "legacy112":
        build_legacy112(repo, args.task)
    elif args.target == "fabric1165":
        build_fabric1165(repo, args.task)
    elif args.target == "fabric120":
        build_fabric120(repo, args.task)
    elif args.target == "fabric121":
        build_fabric121(repo, args.task)
    elif args.target == "forge1165":
        build_forge1165(repo, args.task)
    elif args.target == "modern_all":
        build_fabric1165(repo, args.task)
        build_fabric120(repo, args.task)
        build_fabric121(repo, args.task)
        build_forge1165(repo, args.task)
    else:
        build_legacy112(repo, args.task)
        build_fabric120(repo, args.task)
        build_fabric121(repo, args.task)

    if run_legacy_sync:
        sync_legacy_tablesexport_snapshot(repo)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
