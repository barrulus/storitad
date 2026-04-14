# Storitad

A privacy-first legacy-journaling system. Record voice and video messages on
a phone; process them on your laptop into a durable, browsable archive.
No cloud, no accounts, no Google services.

Storitad is two halves:

1. **Storitad (phone)** — a minimal Android app for GrapheneOS. Records
   audio/video with structured metadata (recipient, mood, tags, notes,
   optional GPS). Writes everything as plain files on shared storage. No
   network permission beyond a one-time Whisper model download the user
   initiates.
2. **Storitad Ingest (laptop/desktop)** — a Python tool that pulls the
   captures over USB, transcribes them with [`whisper.cpp`], generates a
   Markdown source-of-truth archive, and renders a plain static HTML site
   the whole family can browse in any web browser for the next few decades.

Both live in this repository and share a single sidecar format (see
[`storitad-spec.org`](storitad-spec.org) and [`ingest-spec.org`](ingest-spec.org)
for the full specs).

## Why

Voice memos live in a cloud until the cloud forgets them. Photo libraries
are hostage to whoever owns the silo. This is a system where the recordings
live on your filesystem, the archive is plain HTML and Markdown, and none
of it depends on any company staying alive. If the tool ever goes away,
your Markdown files still open in any text editor and your HTML still
renders in any browser.

## Status

- Phone app: stable on Pixel 9 Pro / GrapheneOS. Tags `v0.1.0-mvp`,
  `v0.2.0`, `v0.3.0` cover voice, video, pause/resume, waveform,
  configurable recipients, on-demand Whisper.
- Ingest: MVP shipped. Pulls via adb, transcribes with `whisper-small.en`
  server-side, writes Markdown + static HTML with JS search, writes
  `processed=true` back to the phone so the pending count clears.

## Requirements

### Phone
- A GrapheneOS device (built on Pixel 9 Pro; other Pixels should work).
- Developer options + USB debugging enabled to install the APK the first
  time and for the laptop ingest tool to pull files.

### Laptop / Desktop
- Linux or macOS. Windows is untested; you can probably adapt the commands
  but the ingest flake won't work without [Nix] on WSL.
- [Nix] with flakes enabled (for the reproducible devshell).
- ~500 MB free disk for the Whisper small.en model.
- (Optional) Android SDK + NDK — the Nix flake provides a pinned SDK 35
  and NDK 26 so you don't need to install either yourself.

If you'd rather not use Nix on the laptop, you can install the ingest with
plain `pip` and bring your own `whisper-cli` + `ffmpeg` + `adb` — see
[Manual install](#manual-install-without-nix) below.

## Quick start

### 1. Get the source

```bash
git clone https://github.com/<you>/storitad.git
cd storitad
git submodule update --init --recursive
```

The `whisper.cpp` submodule is needed to build the phone app's native
library.

### 2. Set up the Nix devshell

```bash
nix develop                              # or use direnv: echo 'use flake' > .envrc && direnv allow
```

The shell gives you a pinned Android SDK, Gradle, JDK 17, Python 3.11
(with click/jinja2/pyyaml), `whisper-cli`, `ffmpeg`, and `adb`.

### 3. Build and install the phone app

```bash
./gradlew :app:installDebug
```

With the Pixel plugged in and USB debugging on, the APK installs as
`Storitad`.

**GrapheneOS note:** sideloaded apps have the per-app *Network* toggle off
by default. If you want to use the on-device Transcribe button (which
downloads the tiny.en model once), flip `Settings → Apps → Storitad →
Network` to on. Alternatively, skip phone-side transcription and rely on
the server-side pipeline entirely — it uses a larger, better model anyway.

Permissions the app requests (all GrapheneOS-compatible, all opt-in):

- `RECORD_AUDIO`, `CAMERA` — obvious
- `ACCESS_FINE_LOCATION` — only if you toggle GPS on a specific recording
- `INTERNET` — scoped-comment in the manifest: only used for user-triggered
  Whisper model download; no telemetry, no analytics, nothing else
- `POST_NOTIFICATIONS` + `FOREGROUND_SERVICE_*` — so the recording keeps
  running with a persistent notification if you background the app

### 4. Record something

Open Storitad, tap **Record Voice** (or **Record Video**), hit **Stop**,
tap **Continue**, pick a recipient, hit **Save**. The entry appears on the
**On device** card as pending.

Files land at
`/sdcard/Android/data/uk.storitad.capture/files/Documents/Storitad/inbox/`
on the phone — one `.m4a` or `.mp4` plus a matching `.json` sidecar.

### 5. Set up the ingest

First time only, in the checkout:

```bash
mkdir -p ~/.config/storitad ~/models
cp ingest/config.example.yml  ~/.config/storitad/config.yml
cp ingest/aliases.example.yml ~/.config/storitad/aliases.yml

# Grab the 487 MB server-side transcription model (one-time).
curl -L -o ~/models/ggml-small.en.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin
```

Edit `~/.config/storitad/config.yml` if you want a different archive
location (default is `~/journal`) or model choice.

### 6. Run a sync

With the Pixel plugged in and USB debugging on:

```bash
nix run .#
```

What it does:

1. Pulls all un-synced entries from the phone into `~/journal/.staging/`.
2. For each entry: demuxes audio via ffmpeg (video only) → transcribes
   with `whisper-small.en` → writes a Markdown file at
   `~/journal/entries/YYYY/MM/<basename>.md`, with the media file copied
   alongside.
3. Writes `processed=true` back onto the phone so the **On device** card
   clears.
4. Regenerates the static site at `~/journal/site/` (plain HTML, one CSS
   file, ~50 lines of vanilla JS for search).
5. Opens `site/index.html` in your browser.

That's the loop. Record → Save → plug in → `nix run .#` → browse.

## Day-to-day

```bash
nix run .# -- --dry-run            # pull + report, no writes
nix run .# -- --no-open            # skip xdg-open at the end
nix run .# -- --rerender           # skip pull; just re-render site from existing entries
nix run .# -- --entry 20260414-... # re-ingest a single entry by basename
```

Edit a transcript by editing its `.md` file in any editor, then re-run
`nix run .# -- --rerender`. The Markdown is the source of truth; the HTML
is disposable.

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
    ├── style.css
    ├── search.js
    ├── search-index.json
    └── entries/2026/04/20260414-153200-voice/
        ├── index.html
        └── <media>.m4a
```

Copy the whole folder to a USB stick, sync it somewhere with rsync,
self-host it behind any web server, or just hand someone the directory.
No build step is required to view it.

## Recipient aliases

`ingest/aliases.example.yml` ships a couple of presets for the author's
family. Edit `~/.config/storitad/aliases.yml` to match yours — the phone
app reads from `app/src/main/assets/recipients.json` (rebuild + reinstall
to change), and the ingest uses the YAML config for filtering in the site.

Recipients are currently stored verbatim — if you tag an entry `family` on
the phone, the Markdown will say `recipients: [family]`, not a smeared
list of every person. Alias-based navigation (e.g. show me all entries
touching Casper, regardless of whether they were tagged `family` or
`casper` specifically) is planned but not yet wired up in the site.

## Manual install (without Nix)

If you don't want Nix on the laptop, you need:

- Python ≥ 3.11, with `click`, `jinja2`, `pyyaml` available
- [`whisper.cpp`] installed and on `$PATH` as `whisper-cli` (v1.8.4+)
- `ffmpeg` on `$PATH` (for video entries)
- `adb` on `$PATH` (Android platform-tools)

Then:

```bash
cd ingest && pip install -e .
storitad-pull
```

## Troubleshooting

### Phone restart loop after a voice recording
Fixed in `v0.2.0`. If you see this on an older build, check the Android
logcat for a foreground-service type mismatch; the service now explicitly
passes `FOREGROUND_SERVICE_TYPE_MICROPHONE` on voice and adds `CAMERA`
only for video.

### Whisper model SHA-256 mismatch
The phone verifies the downloaded `ggml-tiny.en.bin` against a hardcoded
SHA. If the upstream file ever changes, update
`app/src/main/java/uk/storitad/capture/whisper/ModelManager.kt` with the
new hash. Same for any model you add to the ingest side — though ingest
doesn't verify yet (a `--fetch-model` helper with SHA is in the Phase 2
backlog).

### GrapheneOS blocks the in-app Whisper download
Either flip the per-app Network toggle in `Settings → Apps → Storitad →
Network`, or use the **Sideload** button in the download dialog and
pick a copy of `ggml-tiny.en.bin` you've already downloaded onto the phone
(the app verifies its SHA-256 either way).

### `adb devices` shows nothing
GrapheneOS: `Settings → About phone` → tap **Build number** 7 times →
`Settings → System → Developer options` → **USB debugging**.

## Architecture

```
                  ┌──────────────────────────────┐
                  │            Storitad            │
                  │          (phone app)           │
                  │                                │
   Record Voice  ─┤ MediaRecorder ──────────────► .m4a
   Record Video  ─┤ CameraX ────────────────────► .mp4
                  │                                │
                  │ EntryMetadata (Kotlin) ──────► sidecar.json
                  │                                │
                  │ (optional) tiny.en transcribe ─► transcriptDraft
                  └──────────────┬───────────────┘
                                 │  USB / adb pull
                                 ▼
                  ┌──────────────────────────────┐
                  │        Storitad Ingest         │
                  │         (Python CLI)           │
                  │                                │
                  │ pull.py (adb)                  │
                  │ sidecar.py (v1+v2 parse)       │
                  │ transcribe.py (ffmpeg+whisper) │
                  │ render_markdown.py (YAML+.md)  │
                  │ render_site.py (Jinja2 HTML    │
                  │                 + JSON search) │
                  │                                │
                  └──────────────┬───────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
        ~/journal/entries/              ~/journal/site/
        (Markdown source                 (static HTML view,
         of truth, YAML                   JS-search-indexed,
         frontmatter)                     regenerate anytime)
```

## Specs

- [`storitad-spec.org`](storitad-spec.org) — Android app (phases 1–3
  shipped, 4+ parked)
- [`ingest-spec.org`](ingest-spec.org) — sixseven pipeline (phase 1
  shipped)

Both are written in Emacs org-mode for the author's convenience; they are
plain text and readable in any editor.

## License

GPL-3.0-or-later. See [`LICENSE`](LICENSE).

This includes the Kotlin Android app, the Python ingest tool, and the
documentation. The vendored `whisper.cpp` submodule is MIT-licensed by
its authors and retains its own licence at `native/whisper.cpp/LICENSE`.

## Acknowledgements

- Whisper and its ggml implementation by Georgi Gerganov and contributors
- GrapheneOS for shipping a usable privacy-respecting Android base
- Anyone who reads their voice memos to the people they love before it's
  too late

[Nix]: https://nixos.org/download/
[`whisper.cpp`]: https://github.com/ggerganov/whisper.cpp
