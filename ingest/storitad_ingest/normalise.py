"""Audio loudness normalisation for video files.

Phone-captured video uses CameraX's `Recorder`, whose default audio source
is `CAMCORDER` — heavily processed and noticeably quieter than the `MIC`
source the voice-only path uses. We compensate at ingest time by passing
the audio track through ffmpeg's `dynaudnorm` (adaptive single-pass
loudness levelling) while stream-copying the video track.

Falls back gracefully: if ffmpeg is missing or the run fails, we copy the
file unchanged so a normalisation issue can never lose a recording.
"""
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path


# Tuned for one-person speech recorded at low input levels; defaults except
# for slightly larger max gain (m=15) so we can lift very quiet captures.
_DYNAUDNORM = "dynaudnorm=p=0.95:m=15"


def ffmpeg_available() -> bool:
    return shutil.which("ffmpeg") is not None


def normalise_to(src: Path, dst: Path) -> bool:
    """Copy `src` → `dst` with audio dynamically normalised. Video stream is
    copied untouched; audio is re-encoded to AAC 128k mono with dynaudnorm.

    Returns True on success, False if ffmpeg failed (caller should fall
    back to a plain copy)."""
    if not ffmpeg_available():
        return False
    try:
        subprocess.check_call([
            "ffmpeg", "-y", "-loglevel", "error",
            "-i", str(src),
            "-c:v", "copy",
            "-c:a", "aac", "-b:a", "128k", "-ac", "1",
            "-af", _DYNAUDNORM,
            "-movflags", "+faststart",
            str(dst),
        ])
        return True
    except subprocess.CalledProcessError:
        # Best-effort: nuke half-written output so the caller can fall back.
        try: dst.unlink()
        except FileNotFoundError: pass
        return False


def normalise_in_place(path: Path) -> bool:
    """Normalise a video in place via temp file + atomic replace.

    Used by the `renorm` command to back-fill the existing archive. Leaves
    the original untouched if ffmpeg fails."""
    with tempfile.NamedTemporaryFile(
        dir=path.parent, prefix=f".{path.stem}.", suffix=path.suffix, delete=False
    ) as tmp:
        tmp_path = Path(tmp.name)
    try:
        if not normalise_to(path, tmp_path):
            return False
        tmp_path.replace(path)
        return True
    finally:
        if tmp_path.exists():
            try: tmp_path.unlink()
            except FileNotFoundError: pass
