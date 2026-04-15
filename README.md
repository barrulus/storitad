# Storitad

A privacy-first legacy-journaling system. Record voice and video messages
on a phone; process them on your laptop into a durable, browsable archive.
No cloud, no accounts, no Google services.

Two halves, one repo:

1. **Storitad (phone)** — a minimal Android app for GrapheneOS. Records
   audio/video with structured metadata (recipient, mood, tags, notes,
   optional GPS). Writes plain files; no network beyond a one-time,
   user-triggered Whisper model download.
2. **Storitad Ingest (laptop/desktop)** — a Python tool that pulls captures
   over USB, transcribes them with [`whisper.cpp`], writes a Markdown
   source-of-truth archive, and renders a plain static HTML site you can
   browse for the next few decades. Optional local server adds in-browser
   edit/delete.

## Why

Voice memos live in a cloud until the cloud forgets them. Photo libraries
are hostage to whoever owns the silo. Storitad keeps your recordings on
your filesystem, the archive as plain Markdown and HTML, and everything
replayable without any vendor staying alive. If the tooling ever dies,
your `.md` files still open in any editor and your HTML still renders in
any browser.

## Status

- Phone app: stable on Pixel 9 Pro / GrapheneOS. Voice, video,
  pause/resume, waveform, configurable recipients, on-demand Whisper,
  timeline-stats History screen.
- Ingest: MVP + quota-based phone cleanup + optional local server with
  in-browser edit/delete. Pulls via adb, transcribes with `whisper-small.en`
  server-side.

## Quick start

```bash
git clone https://github.com/barrulus/storitad.git
cd storitad
git submodule update --init --recursive
nix develop                      # or `direnv allow`
./gradlew :app:installDebug      # install phone app (Pixel plugged in)
nix run .#                       # pull, transcribe, render, open browser
```

Then `Record` on the phone → plug in → `nix run .#` → browse. That's the
loop.

## Documentation

Detailed guides are in [`docs/`](docs):

- [Installation](docs/installation.md) — Nix, manual pip install, Whisper
  models, Android SDK, first-run setup
- [Phone app guide](docs/phone-app.md) — recording, metadata, recipients,
  permissions, GrapheneOS notes, Pending vs History
- [Ingest pipeline](docs/ingest.md) — `storitad-pull`, archive layout,
  cleanup modes (`quota` / `remove_all` / `remove_media`), config, aliases
- [Browser experience](docs/browser.md) — static HTML site, search,
  `stats.html`, how to share or self-host
- [Server mode](docs/server.md) — `storitad-pull serve`, in-browser
  edit/delete, security model (loopback only, no auth)
- [Whisper / transcription](docs/whisper.md) — model choice, SHA
  verification, phone-side tiny.en vs server-side small.en, retranscribe
- [Troubleshooting](docs/troubleshooting.md) — common failures and fixes
- [Architecture](docs/architecture.md) — components, data flow, sidecar
  contract
- [Specs](docs/specs.md) — pointers to `storitad-spec.org` and
  `ingest-spec.org`

## License

GPL-3.0-or-later. See [`LICENSE`](LICENSE). The vendored `whisper.cpp`
submodule retains its own MIT licence at `native/whisper.cpp/LICENSE`.

## Acknowledgements

- Whisper and its ggml implementation by Georgi Gerganov and contributors
- GrapheneOS for shipping a usable privacy-respecting Android base
- Anyone who reads their voice memos to the people they love before it's
  too late

[`whisper.cpp`]: https://github.com/ggerganov/whisper.cpp
