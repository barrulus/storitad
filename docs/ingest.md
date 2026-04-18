# Ingest pipeline

`storitad-pull` is the Python CLI that pulls captures from the phone,
transcribes them, writes a Markdown source-of-truth archive, and renders
a static HTML site.

## Running

Inside the Nix devshell (or with the pip install):

```bash
nix run .#              # full pipeline: pull, transcribe, render, open
# or equivalently:
storitad-pull           # (no subcommand == pull)
storitad-pull pull      # explicit
storitad-pull serve --edit   # see docs/server.md
```

### Flags (on `pull`)

```
--config PATH         ~/.config/storitad/config.yml (override)
--transport adb|mtp   override configured transport (mtp is Phase 2)
--staging PATH        staging dir override (default: <archive>/.staging)
--dry-run             pull + report; no writes
--no-open             skip xdg-open at the end
--rerender            skip pull + transcribe; rebuild site from existing md
--entry BASENAME      re-ingest a single entry
```

## What a run does

1. `adb pull` the phone's inbox into `<archive>/.staging/`.
2. Pair `.json` sidecars with their media files.
3. For each pair:
   - Demux audio via `ffmpeg` (video only) to 16 kHz mono WAV.
   - Transcribe with `whisper-cli` (default model `whisper-small.en`).
   - Write Markdown at `entries/YYYY/MM/<basename>.md`, copy media
     alongside.
   - Run the per-item [cleanup action](#cleanup-modes) for the
     configured mode.
   - Archive the staged sidecar to `processed/YYYY/MM/`.
4. End-of-batch: if cleanup mode is `quota`, evict oldest synced items
   from the phone until the phone inbox is under `quota_gb`.
5. Re-render `site/` from every `.md` under `entries/`.
6. Open `site/index.html` in your browser (unless `--no-open`).

## Archive layout

```
~/journal/
├── entries/                   # Source of truth (git-friendly, human-readable)
│   └── 2026/04/
│       ├── 20260414-153200-voice.md    # YAML frontmatter + transcript + notes
│       └── 20260414-153200-voice.m4a
├── processed/                 # Archived sidecars after a successful sync
│   └── 2026/04/
└── site/                      # Regenerated on every run — safe to delete
    ├── index.html
    ├── stats.html
    ├── style.css
    ├── search.js
    ├── search-index.json
    └── entries/2026/04/20260414-153200-voice/
        ├── index.html
        └── <media>.m4a
```

Only `entries/` needs backing up. `site/` is a pure rebuild artefact.
`processed/` is a local log of what's been ingested — used by the
`quota` cleanup mode to pick eviction candidates, and by every mode to
skip already-ingested items on re-pull.

## Cleanup modes

Configured under `cleanup:` in `~/.config/storitad/config.yml`:

```yaml
cleanup:
  mode: remove_media  # remove_media | remove_all | quota
  quota_gb: 5         # only relevant for quota mode
```

| Mode | What happens per item | End-of-batch |
|------|-----------------------|--------------|
| `remove_media` (default) | Push sidecar back with `processed=true`; `adb shell rm` only the media blob. | No-op. |
| `remove_all` | `adb shell rm` both the media and the sidecar from the phone's inbox immediately. | No-op. |
| `quota` | Leave phone files alone; push sidecar back with `processed=true` so the phone's UI can badge it as synced. | Measure phone inbox; if over `quota_gb`, `adb shell rm` oldest synced items (sidecar + media) until under. |

Every mode also archives the staged sidecar locally to
`~/journal/processed/YYYY/MM/` — that's how `quota` knows which phone
inbox items are safe to evict, and how re-pulls skip items that have
already been ingested. An interrupted sync therefore **cannot** delete
unsynced media from the phone.

Pick `remove_media` (default) if you want the phone to free the big
media blobs while keeping a JSON breadcrumb of what it's sent.
Pick `remove_all` if you want the phone to aggressively free
everything. Pick `quota` if you want offline replay on-device for a
while, with a hard cap on phone storage.

## Configuration

`~/.config/storitad/config.yml`:

```yaml
archive_root: ~/journal
staging: ~/journal/.staging

whisper_bin: whisper-cli
whisper_model: ~/models/ggml-small.en.bin
whisper_model_name: whisper-small.en

transport: adb

cleanup:
  mode: remove_media
  quota_gb: 5

aliases_file: ~/.config/storitad/aliases.yml

# Optional overrides for recipient emoji in the rendered site.
# recipient_emoji:
#   alice: 💙
```

See [Whisper / transcription](whisper.md) for model options.

## Aliases

`~/.config/storitad/aliases.yml`:

```yaml
aliases:
  family: [alice, bob, charlie]
```

The alias map is currently used as a navigation hint only — recipients
are written verbatim to the sidecar (tagging `family` on the phone
yields `recipients: [family]`, not a smeared expansion). Alias-based
filtering in the rendered site is planned.

## Editing an existing entry

The `.md` file is the source of truth. Edit it in any editor, then:

```bash
storitad-pull pull --rerender    # rebuild site from md only
# or, if the local server is running in edit mode:
#   open the entry in the browser, click Edit, make changes, Save
```

See [Server mode](server.md) for the in-browser edit/delete flow.
