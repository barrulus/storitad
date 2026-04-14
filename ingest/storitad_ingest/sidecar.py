"""Sidecar JSON parsing & validation. Accepts v1 and v2 (optional location)."""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


REQUIRED_FIELDS = (
    "id",
    "capturedAt",
    "durationSeconds",
    "timezone",
    "mediaFile",
    "mediaType",
    "mimeType",
    "subject",
    "device",
    "appVersion",
)


@dataclass
class Sidecar:
    raw: dict[str, Any]                   # everything as written by the phone
    path: Path                            # the .json file on disk

    @property
    def id(self) -> str: return self.raw["id"]
    @property
    def basename(self) -> str: return self.path.stem
    @property
    def media_file(self) -> str: return self.raw["mediaFile"]
    @property
    def media_type(self) -> str: return self.raw["mediaType"]  # "VOICE" | "VIDEO"
    @property
    def subject(self) -> str: return self.raw["subject"]
    @property
    def processed(self) -> bool: return self.raw.get("processed", False)
    @property
    def captured_at(self) -> str: return self.raw["capturedAt"]
    @property
    def duration_seconds(self) -> int: return int(self.raw["durationSeconds"])
    @property
    def transcript_draft(self) -> str | None: return self.raw.get("transcriptDraft")
    @property
    def transcript_model(self) -> str | None: return self.raw.get("transcriptModel")
    @property
    def location(self) -> dict[str, Any] | None: return self.raw.get("location")
    @property
    def recipients(self) -> list[str]: return list(self.raw.get("recipients", []))
    @property
    def mood(self) -> str | None: return self.raw.get("mood")
    @property
    def tags(self) -> list[str]: return list(self.raw.get("tags", []))
    @property
    def notes(self) -> str | None: return self.raw.get("notes")


class SidecarError(ValueError):
    pass


def load(path: Path) -> Sidecar:
    raw = json.loads(path.read_text())
    for f in REQUIRED_FIELDS:
        if f not in raw:
            raise SidecarError(f"{path.name}: missing required field {f!r}")
    version = raw.get("version", 1)
    if version not in (1, 2):
        raise SidecarError(f"{path.name}: unsupported sidecar version {version}")
    return Sidecar(raw=raw, path=path)


def load_dir(inbox: Path) -> list[Sidecar]:
    out: list[Sidecar] = []
    for p in sorted(inbox.glob("*.json")):
        try:
            out.append(load(p))
        except SidecarError as e:
            print(f"skip: {e}")
    return out
