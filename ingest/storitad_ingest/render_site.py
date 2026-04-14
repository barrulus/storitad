"""Render the static HTML site + JSON search index from Markdown entries."""
from __future__ import annotations

import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path

import yaml
from jinja2 import Environment, FileSystemLoader, select_autoescape


# Canonical recipient emoji map, mirrors app/src/main/assets/recipients.json.
DEFAULT_RECIPIENT_EMOJI: dict[str, str] = {
    "griffin": "🎧",
    "julian": "🎲",
    "casper": "🎮",
    "emma": "💛",
    "family": "🏠",
    "general": "📝",
}


def _parse_md(path: Path) -> tuple[dict, str]:
    """Split frontmatter + body."""
    text = path.read_text()
    if not text.startswith("---"):
        return {}, text
    _, fm, body = text.split("---", 2)
    data = yaml.safe_load(fm) or {}
    return data, body.strip()


def _body_sections(body: str) -> tuple[str, str]:
    """Return (transcript, notes) extracted from the markdown body."""
    parts = re.split(r"(?m)^## ", body)
    # parts[0] is pre-header content, usually empty
    sections: dict[str, str] = {}
    for chunk in parts[1:]:
        lines = chunk.splitlines()
        if not lines: continue
        name = lines[0].strip().lower()
        sections[name] = "\n".join(lines[1:]).strip()
    t = sections.get("transcript", "")
    if t == "(no transcript)": t = ""
    return t, sections.get("notes", "")


def _chips(fm: dict, emoji: dict[str, str]) -> list[str]:
    chips: list[str] = []
    for r in fm.get("recipients") or []:
        e = emoji.get(r, "👤")
        chips.append(f"{e} {r}")
    mood = fm.get("mood")
    if mood: chips.append(f"• {mood}")
    if fm.get("location"): chips.append("📍")
    return chips


def _format_duration(seconds: int) -> str:
    m, s = divmod(int(seconds or 0), 60)
    return f"{m}m {s:02d}s" if m else f"{s}s"


def _format_date(iso: str) -> str:
    dt = datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone(timezone.utc)
    return dt.strftime("%Y-%m-%d")


def _format_date_long(iso: str) -> str:
    dt = datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone(timezone.utc)
    return dt.strftime("%d %b %Y, %H:%M UTC")


def _month_label(iso: str) -> str:
    dt = datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone(timezone.utc)
    return dt.strftime("%B %Y")


def _template_env() -> Environment:
    tpl_dir = Path(__file__).parent / "templates"
    return Environment(
        loader=FileSystemLoader(str(tpl_dir)),
        autoescape=select_autoescape(["html", "j2"]),
        trim_blocks=False, lstrip_blocks=False,
    )


def render(archive_root: Path, recipient_emoji: dict[str, str] | None = None) -> None:
    emoji = {**DEFAULT_RECIPIENT_EMOJI, **(recipient_emoji or {})}
    entries_root = archive_root / "entries"
    site = archive_root / "site"
    if site.exists():
        shutil.rmtree(site)
    site.mkdir(parents=True)

    # Copy style + search into the site root
    tpl_dir = Path(__file__).parent / "templates"
    shutil.copy2(tpl_dir / "style.css", site / "style.css")
    shutil.copy2(tpl_dir / "search.js", site / "search.js")

    env = _template_env()
    entry_tpl = env.get_template("entry.html.j2")
    index_tpl = env.get_template("index.html.j2")

    # Gather entries
    raw: list[tuple[Path, dict, str, str]] = []   # (md_path, fm, transcript, notes)
    for md in sorted(entries_root.rglob("*.md")):
        fm, body = _parse_md(md)
        if not fm.get("id"): continue
        t, n = _body_sections(body)
        raw.append((md, fm, t, n))

    raw.sort(key=lambda x: x[1].get("captured_at", ""), reverse=True)

    index_entries = []
    search_index = []
    recipients_seen: dict[str, int] = {}

    for md, fm, transcript, notes in raw:
        basename = md.stem
        iso = fm.get("captured_at", "")
        # entries/YYYY/MM/<basename>.md →
        # site/entries/YYYY/MM/<basename>/index.html (+ media.m4a copy)
        rel_dir = md.relative_to(entries_root).parent  # YYYY/MM
        out_dir = site / "entries" / rel_dir / basename
        out_dir.mkdir(parents=True, exist_ok=True)

        media_src = md.with_name(fm.get("media") or "")
        media_name = fm.get("media") or ""
        if media_src.exists():
            shutil.copy2(media_src, out_dir / media_name)

        chips = _chips(fm, emoji)
        loc = fm.get("location")
        location_display = None
        if loc:
            lat = loc.get("latitude"); lng = loc.get("longitude")
            acc = loc.get("accuracy_m")
            if lat is not None and lng is not None:
                tail = f" · ±{int(acc)} m" if acc else ""
                location_display = f"{lat:.4f}, {lng:.4f}{tail}"

        recipients_display = ", ".join(
            f"{emoji.get(r, '👤')} {r}" for r in (fm.get("recipients") or [])
        )

        entry = {
            "id": fm["id"],
            "type": fm.get("type", "voice"),
            "subject": fm.get("subject", basename),
            "date_long": _format_date_long(iso) if iso else "",
            "duration": _format_duration(fm.get("duration_seconds") or 0),
            "media": media_name,
            "transcript": transcript,
            "notes": notes,
            "phone_transcript": fm.get("phone_transcript"),
            "chips": chips,
            "recipients": fm.get("recipients") or [],
            "recipients_display": recipients_display,
            "mood": fm.get("mood"),
            "tags": fm.get("tags") or [],
            "location_display": location_display,
            "device": fm.get("device", ""),
            "captured_at": iso,
            "transcript_model": fm.get("transcript_model"),
        }

        # Each entry page lives 3 levels deep → ../../../../style.css would
        # be five ../, but we chose to inline via explicit path.
        depth_to_site = len(out_dir.relative_to(site).parts)
        back = "../" * depth_to_site + "index.html"
        css  = "../" * depth_to_site + "style.css"
        (out_dir / "index.html").write_text(
            entry_tpl.render(entry=entry, root_css=css, back_url=back)
        )

        url_from_site = "/".join(("entries", *rel_dir.parts, basename, "index.html"))

        index_entries.append({
            "id": entry["id"],
            "url": url_from_site,
            "icon": "🎙️" if entry["type"] == "voice" else "🎥",
            "subject": entry["subject"],
            "date": _format_date(iso) if iso else "",
            "month_label": _month_label(iso) if iso else "",
            "duration": entry["duration"],
            "chips": chips,
        })

        for r in entry["recipients"]:
            recipients_seen[r] = recipients_seen.get(r, 0) + 1

        # Search: server transcript is authoritative; skip phone transcript
        text_blob = " ".join([
            entry["subject"].lower(),
            (transcript or "").lower(),
            (notes or "").lower(),
            " ".join(entry["tags"]).lower(),
        ])
        search_index.append({
            "id": entry["id"],
            "subject": entry["subject"],
            "text": re.sub(r"\s+", " ", text_blob)[:2048],
            "recipients": entry["recipients"],
            "mood": entry["mood"],
            "date": _format_date(iso) if iso else "",
        })

    recipient_filters = [
        {"id": r, "emoji": emoji.get(r, "👤"), "label": r.capitalize()}
        for r, _ in sorted(recipients_seen.items(), key=lambda kv: -kv[1])
    ]

    (site / "index.html").write_text(
        index_tpl.render(entries=index_entries, recipient_filters=recipient_filters)
    )
    (site / "search-index.json").write_text(json.dumps(search_index, ensure_ascii=False))
