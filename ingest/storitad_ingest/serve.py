"""Local HTTP server for the rendered journal.

Read-only by default — just a convenience over `file://`. With `--edit`, mounts
a minimal JSON API for deleting entries and patching subject/notes/tags; source
markdown is rewritten in place and the site is re-rendered on every mutation.

Never bind to a non-loopback address — this has no auth and lets the client
rewrite files on disk.
"""
from __future__ import annotations

import json
import shutil
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Callable

import yaml

from . import render_site


def _find_entry_md(entries_root: Path, entry_id: str) -> Path | None:
    """Locate the source markdown for an entry by its frontmatter `id`.

    entry_id is typically the basename (e.g. `20260414-153200-voice`) so the
    filename lookup is the fast path; we fall back to scanning only if that
    misses (e.g. manual rename)."""
    for candidate in entries_root.rglob(f"{entry_id}.md"):
        return candidate
    for md in entries_root.rglob("*.md"):
        text = md.read_text()
        if not text.startswith("---"):
            continue
        try:
            _, fm, _ = text.split("---", 2)
            if (yaml.safe_load(fm) or {}).get("id") == entry_id:
                return md
        except Exception:
            continue
    return None


def _load_fm(md: Path) -> tuple[dict, str]:
    text = md.read_text()
    if not text.startswith("---"):
        return {}, text
    _, fm_block, body = text.split("---", 2)
    return yaml.safe_load(fm_block) or {}, body


def _write_fm(md: Path, fm: dict, body: str) -> None:
    md.write_text("---\n" + yaml.safe_dump(fm, sort_keys=False).rstrip() + "\n---\n" + body.lstrip("\n") + ("\n" if not body.endswith("\n") else ""))


def _replace_notes_section(body: str, notes: str) -> str:
    """Replace the `## Notes` section in the markdown body. If `notes` is empty,
    drop the section entirely. If the section is absent and `notes` is set,
    append a new one."""
    lines = body.splitlines()
    start = None
    end = len(lines)
    for i, line in enumerate(lines):
        if line.strip().lower() == "## notes":
            start = i
        elif start is not None and line.startswith("## "):
            end = i
            break
    if start is None:
        if not notes:
            return body
        sep = "" if body.endswith("\n\n") else ("\n" if body.endswith("\n") else "\n\n")
        return body + f"{sep}## Notes\n\n{notes}\n"
    if not notes:
        new = lines[:start] + lines[end:]
    else:
        new = lines[:start] + ["## Notes", "", notes, ""] + lines[end:]
    return "\n".join(new).rstrip() + "\n"


def _rerender(cfg) -> None:
    render_site.render(cfg.archive_root, cfg.recipient_emoji, edit_mode=True)


def _api_delete(cfg, entry_id: str) -> tuple[int, bytes]:
    entries_root = cfg.archive_root / "entries"
    md = _find_entry_md(entries_root, entry_id)
    if md is None:
        return HTTPStatus.NOT_FOUND, b"entry not found"
    fm, _ = _load_fm(md)
    media_name = fm.get("media")
    try:
        md.unlink()
        if media_name:
            media = md.with_name(media_name)
            if media.exists(): media.unlink()
    except Exception as e:
        return HTTPStatus.INTERNAL_SERVER_ERROR, f"delete failed: {e}".encode()
    # Blow away the rendered entry dir too so stale files don't linger between renders
    rel = md.relative_to(entries_root).parent
    out_dir = cfg.archive_root / "site" / "entries" / rel / entry_id
    if out_dir.exists(): shutil.rmtree(out_dir, ignore_errors=True)
    _rerender(cfg)
    return HTTPStatus.NO_CONTENT, b""


def _api_patch(cfg, entry_id: str, payload: dict) -> tuple[int, bytes]:
    entries_root = cfg.archive_root / "entries"
    md = _find_entry_md(entries_root, entry_id)
    if md is None:
        return HTTPStatus.NOT_FOUND, b"entry not found"
    fm, body = _load_fm(md)
    if "subject" in payload:
        fm["subject"] = str(payload["subject"]).strip() or fm.get("subject", "")
    if "tags" in payload:
        tags = payload["tags"]
        fm["tags"] = [str(t).strip() for t in tags if str(t).strip()]
    if "notes" in payload:
        body = _replace_notes_section(body, str(payload["notes"]).strip())
    _write_fm(md, fm, body)
    _rerender(cfg)
    return HTTPStatus.NO_CONTENT, b""


def make_handler(cfg, edit: bool) -> type[SimpleHTTPRequestHandler]:
    site_dir = str((cfg.archive_root / "site").resolve())

    class Handler(SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=site_dir, **kwargs)

        def log_message(self, format: str, *args) -> None:  # quieter
            pass

        def _api_route(self) -> tuple[str, str] | None:
            # Matches /api/entry/<id>
            parts = [p for p in self.path.split("?")[0].split("/") if p]
            if len(parts) == 3 and parts[0] == "api" and parts[1] == "entry":
                return "entry", parts[2]
            return None

        def _send(self, status: int, body: bytes, content_type: str = "text/plain") -> None:
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if body:
                self.wfile.write(body)

        def do_DELETE(self) -> None:
            if not edit:
                return self._send(HTTPStatus.METHOD_NOT_ALLOWED, b"edit mode disabled")
            route = self._api_route()
            if not route:
                return self._send(HTTPStatus.NOT_FOUND, b"no such endpoint")
            status, body = _api_delete(cfg, route[1])
            self._send(status, body)

        def do_PATCH(self) -> None:
            if not edit:
                return self._send(HTTPStatus.METHOD_NOT_ALLOWED, b"edit mode disabled")
            route = self._api_route()
            if not route:
                return self._send(HTTPStatus.NOT_FOUND, b"no such endpoint")
            length = int(self.headers.get("Content-Length") or 0)
            try:
                payload = json.loads(self.rfile.read(length) or b"{}")
            except json.JSONDecodeError:
                return self._send(HTTPStatus.BAD_REQUEST, b"invalid json")
            status, body = _api_patch(cfg, route[1], payload)
            self._send(status, body)

    return Handler


def serve(cfg, port: int, edit: bool, render_fn: Callable[[], None]) -> None:
    # Always re-render before serving so the site reflects disk state and the
    # edit-mode flag (edit controls appear only when rendered with edit=True).
    render_fn()
    handler = make_handler(cfg, edit=edit)
    httpd = ThreadingHTTPServer(("127.0.0.1", port), handler)
    mode = "edit" if edit else "read-only"
    print(f"Serving {cfg.archive_root / 'site'} at http://127.0.0.1:{port}/ ({mode})")
    print("Ctrl-C to stop.")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print()
    finally:
        httpd.server_close()
