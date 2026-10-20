#!/usr/bin/env python3
"""Create a local, allowlisted public source snapshot. No Git or network mutations."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INCLUDE = [
    ".gitignore", ".github", "README.md", "LICENSE", "NOTICE", "docs",
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradlew", "gradlew.bat", "gradle/wrapper",
    "app/build.gradle.kts", "app/src/main", "app/src/public", "app/src/debug",
    "app/src/test", "app/src/androidTest", "app/src/androidTestPublic",
    "tools/export_public_source.py", "tools/probe_player_reliability_emulator.py",
    "tools/music-fixture", "tools/stability-fixture", "tools/bitchord-fixture", "tools/catalog-fixture",
]
BLOCKED_PARTS = {".git", ".gradle", "build", "work", "__pycache__", "personal", "testPersonal", "androidTestPersonal"}
BLOCKED_SUFFIXES = {".apk", ".aab", ".jks", ".keystore", ".pyc", ".hprof"}


def selected_files():
    selected = set()
    for entry in INCLUDE:
        source = ROOT / entry
        if not source.exists():
            raise ValueError(f"Missing required source: {entry}")
        for file in sorted(source.rglob("*")) if source.is_dir() else [source]:
            relative = file.relative_to(ROOT)
            if set(relative.parts) & BLOCKED_PARTS:
                continue
            if file.is_symlink():
                raise ValueError(f"Symlinks are not exported: {relative}")
            if not file.is_file():
                continue
            if file.suffix in BLOCKED_SUFFIXES or file.name in {"local.properties", ".DS_Store"}:
                continue
            if file.name.startswith(".env") or "signing" in file.name.lower() or "keystore" in file.name.lower():
                raise ValueError(f"Unexpected credential-like file: {relative}")
            selected.add(relative)
    return sorted(selected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="New local directory; must not already exist")
    args = parser.parse_args()
    destination = args.output.resolve()
    if destination.exists():
        parser.error("Output already exists; choose a new directory")
    if destination == ROOT or ROOT.is_relative_to(destination):
        parser.error("Output cannot contain the source project")
    files = selected_files()
    # Never copy first and filter later: private files must not enter the snapshot at all.
    manifest = []
    destination.mkdir(parents=True)
    for relative in files:
        source = ROOT / relative
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        manifest.append({"path": relative.as_posix(), "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    (destination / "SOURCE-MANIFEST.json").write_text(json.dumps({"project": "GlanceBeat", "edition": "public", "files": manifest}, indent=2) + "\n")
    print(f"Prepared {len(files)} public source files in {destination}")


if __name__ == "__main__":
    main()
