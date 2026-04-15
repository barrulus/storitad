"""Transport layer: pull .json + media from the phone.

Phase 1 supports adb only. mtp is Phase 2.
"""
from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path


PHONE_INBOX_PATH = "/sdcard/Android/data/uk.storitad.capture/files/Documents/Storitad/inbox"


@dataclass
class PullResult:
    staging: Path
    pairs: list[tuple[Path, Path]]        # (sidecar, media)
    orphans: list[Path]                   # files that couldn't be paired


def _adb(*args: str) -> str:
    return subprocess.check_output(["adb", *args], text=True)


def phone_rm(*names: str) -> None:
    """Delete files from the phone's inbox by basename. Silent on ENOENT."""
    if not names:
        return
    paths = " ".join(f"'{PHONE_INBOX_PATH}/{n}'" for n in names)
    subprocess.call(["adb", "shell", f"rm -f {paths}"], stdout=subprocess.DEVNULL)


def phone_inbox_listing() -> list[tuple[str, int]]:
    """Return [(filename, size_bytes)] for everything currently in the phone's inbox."""
    try:
        out = _adb("shell", f"ls -la '{PHONE_INBOX_PATH}' 2>/dev/null")
    except subprocess.CalledProcessError:
        return []
    rows: list[tuple[str, int]] = []
    for line in out.splitlines():
        parts = line.split()
        # Typical: -rw-rw---- 1 u0_a123 ext_data_rw 1234567 2026-04-01 12:00 foo.m4a
        if len(parts) < 8 or not parts[0].startswith("-"):
            continue
        try:
            size = int(parts[4])
        except ValueError:
            continue
        name = parts[-1]
        rows.append((name, size))
    return rows


def _adb_ok() -> bool:
    try:
        out = _adb("devices")
    except (FileNotFoundError, subprocess.CalledProcessError):
        return False
    return any(line.strip().endswith("\tdevice") for line in out.splitlines()[1:])


def pull_adb(staging: Path) -> PullResult:
    if not _adb_ok():
        raise RuntimeError("no adb device connected (is USB debugging on?)")

    staging.mkdir(parents=True, exist_ok=True)
    # Wipe any stale staging contents from a prior failed run.
    for p in staging.iterdir():
        if p.is_dir(): shutil.rmtree(p)
        else: p.unlink()

    # `adb pull <dir>` recreates the remote dir under staging; so pull a
    # nested path and flatten.
    tmp = staging / "_inbox"
    subprocess.check_call(["adb", "pull", PHONE_INBOX_PATH, str(tmp)])

    for child in tmp.rglob("*"):
        if child.is_file():
            shutil.move(str(child), str(staging / child.name))
    shutil.rmtree(tmp, ignore_errors=True)

    return _pair(staging)


def _pair(staging: Path) -> PullResult:
    sidecars = sorted(staging.glob("*.json"))
    pairs: list[tuple[Path, Path]] = []
    orphans: list[Path] = []
    for j in sidecars:
        # Read the sidecar just enough to find its media filename.
        try:
            import json
            media_name = json.loads(j.read_text()).get("mediaFile")
        except Exception:
            orphans.append(j); continue
        if not media_name:
            orphans.append(j); continue
        media = staging / media_name
        if media.exists():
            pairs.append((j, media))
        else:
            orphans.append(j)
    return PullResult(staging=staging, pairs=pairs, orphans=orphans)
