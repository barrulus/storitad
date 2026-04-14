"""End-to-end test with hand-crafted sidecars — no phone, no whisper."""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from storitad_ingest import aliases, render_markdown, render_site, sidecar


def _write_sidecar(d: Path, basename: str, data: dict) -> tuple[Path, Path]:
    j = d / f"{basename}.json"
    m = d / data["mediaFile"]
    j.write_text(json.dumps(data))
    m.write_bytes(b"fake audio bytes")   # content doesn't matter for non-whisper tests
    return j, m


def _sample(basename: str, **overrides) -> dict:
    data = {
        "id": basename,
        "version": 2,
        "capturedAt": "2026-04-14T15:32:00Z",
        "durationSeconds": 154,
        "timezone": "Europe/London",
        "mediaFile": f"{basename}.m4a",
        "mediaType": "VOICE",
        "mimeType": "audio/mp4",
        "subject": "Picking Casper up from school",
        "recipients": ["family"],
        "mood": "happy",
        "tags": ["school", "daily"],
        "notes": "He beat the impossible level",
        "device": "Pixel 9 Pro",
        "appVersion": "0.3.0",
        "location": {"latitude": 51.5074, "longitude": -0.1278, "accuracyMeters": 12.4,
                     "capturedAt": "2026-04-14T15:32:05Z"},
    }
    data.update(overrides)
    return data


def test_full_render(tmp_path: Path):
    inbox = tmp_path / "inbox"
    inbox.mkdir()
    archive = tmp_path / "journal"

    j1, m1 = _write_sidecar(inbox, "20260414-153200-voice",
                            _sample("20260414-153200-voice"))
    j2, m2 = _write_sidecar(inbox, "20260413-100000-voice",
                            _sample("20260413-100000-voice",
                                    capturedAt="2026-04-13T10:00:00Z",
                                    subject="Walk at Rhossili",
                                    recipients=["emma"],
                                    mood="calm",
                                    tags=["walk"],
                                    notes=None,
                                    mediaFile="20260413-100000-voice.m4a"))
    j3, m3 = _write_sidecar(inbox, "20260412-094500-voice",
                            _sample("20260412-094500-voice",
                                    capturedAt="2026-04-12T09:45:00Z",
                                    subject="Untargeted note",
                                    recipients=["general"],
                                    mood="reflective",
                                    tags=[],
                                    notes=None,
                                    location=None,
                                    mediaFile="20260412-094500-voice.m4a"))

    for j, m in [(j1, m1), (j2, m2), (j3, m3)]:
        sc = sidecar.load(j)
        # Recipients are stored verbatim now — no auto-expansion.
        render_markdown.write_entry(
            root=archive, sc=sc, media_src=m,
            server_transcript=f"This is the transcript for {sc.subject}.",
            server_model="whisper-small.en",
        )

    # Markdown files exist with the expected structure
    april = archive / "entries" / "2026" / "04"
    mds = sorted(april.glob("*.md"))
    assert len(mds) == 3
    text = mds[0].read_text()
    assert text.startswith("---")
    assert "subject:" in text
    assert "## Transcript" in text

    # Media file copied next to markdown
    assert (april / "20260414-153200-voice.m4a").exists()

    # recipients are stored verbatim — family is NOT auto-expanded
    import yaml as _yaml
    fm = _yaml.safe_load(mds[2].read_text().split("---", 2)[1])
    assert fm["recipients"] == ["family"]

    # Site renders
    render_site.render(archive)
    site = archive / "site"
    assert (site / "index.html").exists()
    assert (site / "style.css").exists()
    assert (site / "search.js").exists()
    idx = json.loads((site / "search-index.json").read_text())
    assert len(idx) == 3
    subjects = {e["subject"] for e in idx}
    assert "Picking Casper up from school" in subjects

    # Each entry page exists
    for basename in ("20260414-153200-voice", "20260413-100000-voice", "20260412-094500-voice"):
        page = site / "entries" / "2026" / "04" / basename / "index.html"
        assert page.exists(), f"missing {page}"
        html = page.read_text()
        assert "<audio" in html
        assert "This is the transcript for" in html

    # search blob contains subject + transcript + notes (lowercase)
    casper_rec = next(e for e in idx if "casper" in e["subject"].lower())
    assert "casper" in casper_rec["text"]
    assert "impossible level" in casper_rec["text"]


def test_aliases_expand(tmp_path: Path):
    # Default ship-with map is empty; load from an explicit YAML.
    yml = tmp_path / "aliases.yml"
    yml.write_text("aliases:\n  family: [alice, bob]\n")
    m = aliases.load_aliases(yml)
    out = aliases.expand(["family", "carol"], m)
    assert out[0] == "family"
    assert "alice" in out and "bob" in out and "carol" in out
    # no duplicates
    assert len(out) == len(set(out))


def test_aliases_empty_by_default():
    assert aliases.load_aliases(None) == {}


def test_sidecar_v1_without_location(tmp_path: Path):
    inbox = tmp_path / "inbox"; inbox.mkdir()
    data = _sample("x-voice")
    data["version"] = 1
    data.pop("location")
    j = inbox / "x-voice.json"
    j.write_text(json.dumps(data))
    sc = sidecar.load(j)
    assert sc.location is None
    assert sc.recipients == ["family"]
