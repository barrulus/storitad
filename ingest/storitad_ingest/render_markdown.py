"""Write one Markdown file per entry at entries/YYYY/MM/{basename}.md,
plus a copy of the media file next to it."""
from __future__ import annotations

import shutil
from datetime import datetime, timezone
from pathlib import Path

import yaml

from . import normalise
from .sidecar import Sidecar


def entry_dir(root: Path, captured_at: str) -> Path:
    # captured_at is always Z / ISO 8601; parse safely.
    dt = datetime.fromisoformat(captured_at.replace("Z", "+00:00")).astimezone(timezone.utc)
    return root / "entries" / f"{dt.year:04d}" / f"{dt.month:02d}"


def _frontmatter(sc: Sidecar, server_transcript: str | None, server_model: str | None) -> dict:
    loc = sc.location
    fm: dict = {
        "id": sc.id,
        "type": sc.media_type.lower(),
        "captured_at": sc.captured_at,
        "timezone": sc.raw.get("timezone"),
        "duration_seconds": sc.duration_seconds,
        "subject": sc.subject,
        "recipients": sc.recipients,
        "mood": sc.mood,
        "tags": sc.tags,
        "device": sc.raw.get("device"),
        "media": sc.media_file,
    }
    if loc:
        fm["location"] = {
            "latitude": loc.get("latitude"),
            "longitude": loc.get("longitude"),
            "accuracy_m": loc.get("accuracyMeters"),
        }
    if server_transcript is not None:
        fm["transcript_model"] = server_model
    if sc.transcript_draft:
        fm["phone_transcript"] = sc.transcript_draft
        fm["phone_transcript_model"] = sc.transcript_model
    # strip null values for a tidier file
    return {k: v for k, v in fm.items() if v not in (None, [], {})}


def write_entry(
    root: Path,
    sc: Sidecar,
    media_src: Path,
    server_transcript: str | None,
    server_model: str | None,
) -> Path:
    out_dir = entry_dir(root, sc.captured_at)
    out_dir.mkdir(parents=True, exist_ok=True)
    md_path = out_dir / f"{sc.basename}.md"
    media_dst = out_dir / sc.media_file

    fm = _frontmatter(sc, server_transcript, server_model)
    body_transcript = (server_transcript or "").strip() or "(no transcript)"

    parts = [
        "---",
        yaml.safe_dump(fm, sort_keys=False, allow_unicode=True).rstrip(),
        "---",
        "",
        "## Transcript",
        "",
        body_transcript,
        "",
    ]
    if sc.notes:
        parts += ["## Notes", "", sc.notes.strip(), ""]

    md_path.write_text("\n".join(parts))
    if media_src != media_dst:
        # Video: normalise audio while copying (CameraX records via the
        # CAMCORDER source which is noticeably quieter than the voice path's
        # MIC source). Voice .m4a is already loud enough — straight copy.
        is_video = sc.media_type.lower() == "video"
        if is_video and normalise.normalise_to(media_src, media_dst):
            pass
        else:
            shutil.copy2(media_src, media_dst)
    return md_path
