# Architecture

## Data flow

```
                  ┌──────────────────────────────┐
                  │           Storitad            │
                  │          (phone app)          │
                  │                               │
   Record Voice  ─┤ MediaRecorder ─────────────► .m4a
   Record Video  ─┤ CameraX ───────────────────► .mp4
                  │                               │
                  │ EntryMetadata (Kotlin) ─────► sidecar.json
                  │                               │
                  │ (optional) tiny.en transcribe ► transcriptDraft
                  └──────────────┬────────────────┘
                                 │  USB / adb
                                 ▼
                  ┌──────────────────────────────┐
                  │        Storitad Ingest        │
                  │         (Python CLI)          │
                  │                               │
                  │ pull.py       (adb)           │
                  │ sidecar.py    (v1+v2 parse)   │
                  │ transcribe.py (ffmpeg+whisper)│
                  │ render_markdown.py            │
                  │ render_site.py                │
                  │ serve.py      (optional)      │
                  │ cli.py        (click group)   │
                  └──────────────┬────────────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          ▼                      ▼                      ▼
  ~/journal/entries/     ~/journal/site/       127.0.0.1:<port>
  (Markdown + media,     (static HTML,         (optional server;
   source of truth)      regenerate anytime)    edit/delete APIs)
```

## Components

### Phone app (`app/`)
- Kotlin, Jetpack Compose UI.
- Single activity, Compose Navigation; routes: Home, Recording,
  VideoRecording, Review, Metadata, Pending, History (stats dashboard),
  Detail, Settings.
- `MetadataRepository` reads/writes `EntryMetadata` sidecars from the
  app's scoped storage (shared `Documents/Storitad/inbox/`).
- `RecorderService` is a foreground service with type matching the
  runtime permission (`MICROPHONE` for voice, `MICROPHONE|CAMERA` for
  video) to avoid Android 14+ insta-kill.
- `ModelManager` handles the optional `ggml-tiny.en.bin` download with
  SHA-256 verification.

### Ingest (`ingest/storitad_ingest/`)
- `cli.py` — click group with `pull` (default) and `serve` subcommands.
- `pull.py` — adb transport, staging pair resolution, phone-side rm /
  ls helpers.
- `sidecar.py` — validates + parses v1 and v2 sidecars (v1 is legacy
  without location).
- `transcribe.py` — wraps `whisper-cli`; ffmpeg demux for video.
- `render_markdown.py` — writes YAML frontmatter + body, copies media.
- `render_site.py` — Jinja2 templates → HTML; computes stats for
  `stats.html`; builds the JSON search index.
- `serve.py` — stdlib `ThreadingHTTPServer`, loopback-only, optional
  edit API.
- `aliases.py` — alias map loader (used as navigation hint, not for
  expansion on write).
- `templates/` — Jinja2 `index.html.j2`, `entry.html.j2`,
  `stats.html.j2`, `style.css`, `search.js`.

## Sidecar contract

Every entry is `<basename>.json` + a matching media file (`.m4a` or
`.mp4`). Versioning: v1 (pre-GPS) and v2 (with optional `location`
block) are both accepted on ingest; unknown fields pass through.

Canonical shape (from `EntryMetadata.kt` on the phone side and
`sidecar.py` on the ingest side):

```json
{
  "id": "20260414-153200-voice",
  "version": 2,
  "capturedAt": "2026-04-14T15:32:00Z",
  "durationSeconds": 154,
  "timezone": "Europe/London",
  "mediaFile": "20260414-153200-voice.m4a",
  "mediaType": "VOICE",
  "mimeType": "audio/mp4",
  "subject": "Picking Casper up from school",
  "recipients": ["family"],
  "mood": "happy",
  "tags": ["school", "daily"],
  "notes": "...",
  "location": {
    "latitude": 51.5074, "longitude": -0.1278,
    "accuracyMeters": 12.4,
    "capturedAt": "2026-04-14T15:32:05Z"
  },
  "device": "Pixel 9 Pro",
  "appVersion": "0.3.0",
  "transcriptDraft": "...",
  "transcriptModel": "whisper-tiny.en",
  "processed": false
}
```

`processed` is the handshake field: the ingest sets it to `true` and
pushes the sidecar back (on `quota` or `remove_media` modes). The
phone's Pending list filters on `!processed`.

## Markdown frontmatter

Derived from the sidecar, plus server-side transcription additions:

```yaml
---
id: 20260414-153200-voice
type: voice                       # voice | video
subject: Picking Casper up from school
captured_at: 2026-04-14T15:32:00Z
duration_seconds: 154
recipients: [family]
mood: happy
tags: [school, daily]
media: 20260414-153200-voice.m4a
device: Pixel 9 Pro
location:
  latitude: 51.5074
  longitude: -0.1278
  accuracy_m: 12.4
transcript_model: whisper-small.en
phone_transcript: "raw tiny.en output, if any"
phone_transcript_model: whisper-tiny.en
---

## Transcript

Server-side whisper-small.en text.

## Notes

Anything I typed in the metadata screen.
```

The Markdown body is the authoritative transcript + notes. The HTML
site is a view, regenerated on every run.

## Design principles

1. **Phone writes files, not databases.** No Room, no SQLite. Every
   capture is a pair of files on shared storage. The ingest cannot
   corrupt anything by reading them.
2. **Markdown is the source of truth.** HTML is disposable.
3. **Heavy lifting on sixseven, not the phone.** Anything beyond
   tiny-model transcription happens on the desktop.
4. **No network unless the user asks.** Phone's Network toggle can stay
   off. Ingest goes over USB only.
5. **The tool going away must not kill the archive.** Markdown opens in
   any editor, HTML in any browser.
