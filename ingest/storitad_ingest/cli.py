"""storitad-pull — pull, ingest, render, open."""
from __future__ import annotations

import os
import webbrowser
from dataclasses import dataclass, field
from pathlib import Path

import click
import yaml

from . import __version__
from . import aliases as aliases_mod
from . import pull, render_markdown, render_site, serve as serve_mod, sidecar, transcribe


DEFAULT_ARCHIVE = Path.home() / "journal"
DEFAULT_CONFIG = Path.home() / ".config" / "storitad" / "config.yml"
DEFAULT_ALIASES = Path.home() / ".config" / "storitad" / "aliases.yml"


@dataclass
class CleanupConfig:
    mode: str = "quota"          # quota | remove_all | remove_media
    quota_gb: float = 5.0


@dataclass
class Config:
    archive_root: Path = DEFAULT_ARCHIVE
    staging: Path | None = None
    whisper_bin: str = "whisper-cli"
    whisper_model: str = ""
    whisper_model_name: str = "whisper-small.en"
    transport: str = "adb"
    aliases_file: Path = DEFAULT_ALIASES
    recipient_emoji: dict[str, str] = field(default_factory=dict)
    cleanup: CleanupConfig = field(default_factory=CleanupConfig)


def _expand(p: Path | str) -> Path:
    return Path(os.path.expandvars(os.path.expanduser(str(p))))


def load_config(path: Path) -> Config:
    c = Config()
    if path.exists():
        data = yaml.safe_load(path.read_text()) or {}
        if "archive_root" in data: c.archive_root = _expand(data["archive_root"])
        if "staging" in data: c.staging = _expand(data["staging"])
        if "whisper_bin" in data: c.whisper_bin = str(data["whisper_bin"])
        if "whisper_model" in data: c.whisper_model = str(_expand(data["whisper_model"]))
        if "whisper_model_name" in data: c.whisper_model_name = str(data["whisper_model_name"])
        if "transport" in data: c.transport = str(data["transport"])
        if "aliases_file" in data: c.aliases_file = _expand(data["aliases_file"])
        if "recipient_emoji" in data and isinstance(data["recipient_emoji"], dict):
            c.recipient_emoji = {str(k): str(v) for k, v in data["recipient_emoji"].items()}
        if "cleanup" in data and isinstance(data["cleanup"], dict):
            cl = data["cleanup"]
            if "mode" in cl: c.cleanup.mode = str(cl["mode"])
            if "quota_gb" in cl: c.cleanup.quota_gb = float(cl["quota_gb"])
            if c.cleanup.mode not in ("quota", "remove_all", "remove_media"):
                raise click.ClickException(f"cleanup.mode {c.cleanup.mode!r} is not one of quota|remove_all|remove_media")
    if c.staging is None:
        c.staging = c.archive_root / ".staging"
    return c


def _transcribe_cfg(cfg: Config) -> transcribe.TranscriberConfig:
    return transcribe.TranscriberConfig(
        whisper_bin=cfg.whisper_bin,
        model_path=cfg.whisper_model,
        model_name=cfg.whisper_model_name,
    )


def process_pair(sidecar_path: Path, media_path: Path, cfg: Config, alias_map: dict[str, list[str]] | None = None) -> Path:
    sc = sidecar.load(sidecar_path)

    # Recipients are stored verbatim — no auto-expansion. If the user picked
    # `family`, the entry is tagged `family` (not each person individually).
    # Aliases are available as a navigation mechanism in the rendered site
    # if we later choose to filter by alias membership, but we no longer
    # smear the tags so that every entry looks like it's for everyone.

    server_transcript = None
    server_model = None
    try:
        server_transcript = transcribe.transcribe(media_path, _transcribe_cfg(cfg))
        server_model = cfg.whisper_model_name
    except Exception as e:
        click.echo(f"  transcribe skipped: {e}", err=True)

    md = render_markdown.write_entry(
        root=cfg.archive_root,
        sc=sc,
        media_src=media_path,
        server_transcript=server_transcript,
        server_model=server_model,
    )

    try:
        _apply_cleanup_per_item(sidecar_path, sc, cfg)
    except Exception as e:
        click.echo(f"  cleanup skipped: {e}", err=True)

    return md


def _archive_processed(sidecar_path: Path, sc, cfg: Config) -> None:
    """Move the staged sidecar into ~/journal/processed/YYYY/MM/ — regardless of
    cleanup mode we keep a local record of what's been synced, so quota mode
    can later evict from the phone in order."""
    from datetime import datetime, timezone
    dt = datetime.fromisoformat(sc.captured_at.replace("Z", "+00:00")).astimezone(timezone.utc)
    archived = cfg.archive_root / "processed" / f"{dt.year:04d}" / f"{dt.month:02d}"
    archived.mkdir(parents=True, exist_ok=True)
    target = archived / sidecar_path.name
    sidecar_path.replace(target)


def _apply_cleanup_per_item(sidecar_path: Path, sc, cfg: Config) -> None:
    """Per-item cleanup action — runs immediately after a successful render.
    Quota-based eviction is deferred to end-of-batch (see _apply_quota)."""
    import json
    import subprocess

    mode = cfg.cleanup.mode
    if mode == "remove_all":
        pull.phone_rm(sidecar_path.name, sc.media_file)
        _archive_processed(sidecar_path, sc, cfg)
        return

    if mode == "remove_media":
        # Push sidecar back with processed=true so phone's Pending list clears
        # and its own "Processed" tab picks it up; then nuke the media blob.
        sc.raw["processed"] = True
        sidecar_path.write_text(json.dumps(sc.raw, indent=2))
        subprocess.check_call([
            "adb", "push", str(sidecar_path),
            f"{pull.PHONE_INBOX_PATH}/{sidecar_path.name}",
        ], stdout=subprocess.DEVNULL)
        pull.phone_rm(sc.media_file)
        _archive_processed(sidecar_path, sc, cfg)
        return

    # quota: leave phone alone, but mark locally as synced. Also push back
    # processed=true so the phone can badge items as synced in its History.
    sc.raw["processed"] = True
    sidecar_path.write_text(json.dumps(sc.raw, indent=2))
    try:
        subprocess.check_call([
            "adb", "push", str(sidecar_path),
            f"{pull.PHONE_INBOX_PATH}/{sidecar_path.name}",
        ], stdout=subprocess.DEVNULL)
    except subprocess.CalledProcessError:
        pass
    _archive_processed(sidecar_path, sc, cfg)


def _apply_quota(cfg: Config) -> None:
    """End-of-batch: if total phone-inbox size exceeds quota_gb, evict the
    oldest already-synced items from the phone (we know which are synced
    because their sidecars live in ~/journal/processed/)."""
    import json

    quota_bytes = int(cfg.cleanup.quota_gb * (1024 ** 3))
    listing = pull.phone_inbox_listing()
    total = sum(sz for _, sz in listing)
    if total <= quota_bytes:
        click.echo(f"  quota: phone inbox {total / 1e9:.2f} GB / {cfg.cleanup.quota_gb} GB — no eviction")
        return

    synced_root = cfg.archive_root / "processed"
    if not synced_root.exists():
        click.echo("  quota: over limit but no synced items to evict", err=True)
        return

    synced: list[tuple[str, str, str]] = []  # (captured_at, sidecar_name, media_name)
    for j in synced_root.rglob("*.json"):
        try:
            raw = json.loads(j.read_text())
            synced.append((raw.get("capturedAt", ""), j.name, raw.get("mediaFile", "")))
        except Exception:
            continue
    synced.sort()  # oldest first

    on_phone = {name for name, _ in listing}
    size_by_name = dict(listing)

    freed = 0
    evicted = 0
    for _, sidecar_name, media_name in synced:
        if total - freed <= quota_bytes:
            break
        to_delete = [n for n in (sidecar_name, media_name) if n in on_phone]
        if not to_delete:
            continue
        pull.phone_rm(*to_delete)
        freed += sum(size_by_name.get(n, 0) for n in to_delete)
        evicted += 1

    click.echo(f"  quota: evicted {evicted} item(s), freed {freed / 1e9:.2f} GB from phone")


@click.group(invoke_without_command=True)
@click.option("--config", type=click.Path(path_type=Path), default=DEFAULT_CONFIG, show_default=True)
@click.pass_context
def main(ctx: click.Context, config: Path) -> None:
    """Storitad — pull, ingest, render, serve."""
    ctx.ensure_object(dict)
    ctx.obj["config_path"] = config
    if ctx.invoked_subcommand is None:
        ctx.invoke(pull_cmd)


@main.command("pull")
@click.option("--transport", type=click.Choice(["adb", "mtp"]), default=None, help="Override configured transport")
@click.option("--staging", type=click.Path(path_type=Path), default=None, help="Staging dir override")
@click.option("--dry-run", is_flag=True, help="Pull + report; no writes")
@click.option("--no-open", is_flag=True, help="Skip opening the browser at the end")
@click.option("--rerender", is_flag=True, help="Skip pull + transcribe; only rebuild site from existing entries")
@click.option("--entry", "entry_id", default=None, help="Re-ingest a single entry (by basename)")
@click.pass_context
def pull_cmd(ctx: click.Context, transport: str | None, staging: Path | None, dry_run: bool, no_open: bool, rerender: bool, entry_id: str | None) -> None:
    """Pull from the phone, ingest, render, open."""
    config = ctx.obj["config_path"]
    click.echo(f"storitad-ingest {__version__}")
    cfg = load_config(config)
    if transport: cfg.transport = transport
    if staging: cfg.staging = staging
    alias_map = aliases_mod.load_aliases(cfg.aliases_file)

    cfg.archive_root.mkdir(parents=True, exist_ok=True)

    if not rerender:
        if cfg.transport != "adb":
            raise click.ClickException(f"transport {cfg.transport!r} not supported yet (Phase 2)")

        staging_dir = cfg.staging or (cfg.archive_root / ".staging")
        click.echo(f"Pulling → {staging_dir}")
        result = pull.pull_adb(staging_dir)
        click.echo(f"  {len(result.pairs)} pair(s), {len(result.orphans)} orphan(s)")

        if dry_run:
            for j, m in result.pairs:
                click.echo(f"  would process: {j.name} + {m.name}")
            return

        for j, m in result.pairs:
            if entry_id and j.stem != entry_id:
                continue
            click.echo(f"Processing {j.name}")
            try:
                md = process_pair(j, m, cfg, alias_map)
                click.echo(f"  wrote {md.relative_to(cfg.archive_root)}")
            except Exception as e:
                click.echo(f"  FAIL {j.name}: {e}", err=True)

        if cfg.cleanup.mode == "quota":
            try:
                _apply_quota(cfg)
            except Exception as e:
                click.echo(f"  quota enforcement skipped: {e}", err=True)

    click.echo("Rendering site…")
    render_site.render(cfg.archive_root, cfg.recipient_emoji)
    index_html = cfg.archive_root / "site" / "index.html"
    click.echo(f"  {index_html}")

    if not no_open and index_html.exists():
        webbrowser.open(index_html.as_uri())


@main.command("serve")
@click.option("--port", type=int, default=8765, show_default=True)
@click.option("--edit", is_flag=True, help="Enable delete + edit endpoints. Loopback only; no auth.")
@click.option("--no-open", is_flag=True, help="Don't open the browser")
@click.pass_context
def serve_cmd(ctx: click.Context, port: int, edit: bool, no_open: bool) -> None:
    """Serve the rendered site on localhost. With --edit, allows delete + field edits."""
    cfg = load_config(ctx.obj["config_path"])
    cfg.archive_root.mkdir(parents=True, exist_ok=True)

    def _render():
        click.echo("Rendering site…")
        render_site.render(cfg.archive_root, cfg.recipient_emoji, edit_mode=edit)

    if not no_open:
        webbrowser.open(f"http://127.0.0.1:{port}/index.html")

    serve_mod.serve(cfg, port=port, edit=edit, render_fn=_render)


if __name__ == "__main__":
    main()
