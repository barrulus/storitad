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
from . import pull, render_markdown, render_site, sidecar, transcribe


DEFAULT_ARCHIVE = Path.home() / "journal"
DEFAULT_CONFIG = Path.home() / ".config" / "storitad" / "config.yml"
DEFAULT_ALIASES = Path.home() / ".config" / "storitad" / "aliases.yml"


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

    # Phase-2-of-ingest item promoted to Phase 1: mark the sidecar processed
    # on the phone so the Pending list clears.
    try:
        _writeback_processed(sidecar_path, sc, cfg)
    except Exception as e:
        click.echo(f"  writeback skipped: {e}", err=True)

    return md


def _writeback_processed(sidecar_path: Path, sc, cfg: Config) -> None:
    import json
    import subprocess
    from datetime import datetime, timezone

    sc.raw["processed"] = True
    sidecar_path.write_text(json.dumps(sc.raw, indent=2))

    subprocess.check_call([
        "adb", "push", str(sidecar_path),
        f"{pull.PHONE_INBOX_PATH}/{sidecar_path.name}",
    ], stdout=subprocess.DEVNULL)

    dt = datetime.fromisoformat(sc.captured_at.replace("Z", "+00:00")).astimezone(timezone.utc)
    archived = cfg.archive_root / "processed" / f"{dt.year:04d}" / f"{dt.month:02d}"
    archived.mkdir(parents=True, exist_ok=True)
    target = archived / sidecar_path.name
    sidecar_path.replace(target)


@click.command()
@click.option("--config", type=click.Path(path_type=Path), default=DEFAULT_CONFIG, show_default=True)
@click.option("--transport", type=click.Choice(["adb", "mtp"]), default=None, help="Override configured transport")
@click.option("--staging", type=click.Path(path_type=Path), default=None, help="Staging dir override")
@click.option("--dry-run", is_flag=True, help="Pull + report; no writes")
@click.option("--no-open", is_flag=True, help="Skip opening the browser at the end")
@click.option("--rerender", is_flag=True, help="Skip pull + transcribe; only rebuild site from existing entries")
@click.option("--entry", "entry_id", default=None, help="Re-ingest a single entry (by basename)")
def main(config: Path, transport: str | None, staging: Path | None, dry_run: bool, no_open: bool, rerender: bool, entry_id: str | None) -> None:
    """Storitad ingest pipeline."""
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

    click.echo("Rendering site…")
    render_site.render(cfg.archive_root, cfg.recipient_emoji)
    index_html = cfg.archive_root / "site" / "index.html"
    click.echo(f"  {index_html}")

    if not no_open and index_html.exists():
        webbrowser.open(index_html.as_uri())


if __name__ == "__main__":
    main()
