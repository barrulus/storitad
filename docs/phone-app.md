# Phone app guide

Storitad's Android app records voice and video messages with structured
metadata and writes them to shared storage as plain files, ready for the
ingest pipeline to pick up over USB.

## Permissions

All GrapheneOS-compatible and opt-in:

- `RECORD_AUDIO`, `CAMERA` — required for voice and video respectively
- `ACCESS_FINE_LOCATION` — only if you toggle GPS on for a specific
  recording
- `INTERNET` — scoped-only: used for the user-triggered on-device Whisper
  model download. No telemetry, no analytics, no background traffic.
  Leaving the GrapheneOS per-app Network toggle off is a supported mode.
- `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_*` — so recording keeps
  running with a visible notification when the app is backgrounded
- `READ_EXTERNAL_STORAGE` — to preview already-captured media

Per-app Network is off by default on GrapheneOS for sideloaded apps. The
app works fully offline; only the on-device Transcribe feature needs
Network on.

## Recording loop

1. **Home** → tap `Record Voice` or `Record Video`.
2. Record. Pause/resume on voice. Stop when done.
3. **Review** — listen / watch back, optionally retake.
4. **Metadata** — pick recipient(s), mood, tags, notes, optional GPS fix.
5. **Save** — entry lands in the phone inbox and appears on the Home
   screen's **Pending** count.

Files written to:

```
/sdcard/Android/data/uk.storitad.capture/files/Documents/Storitad/inbox/
├── 20260414-153200-voice.m4a
└── 20260414-153200-voice.json   # sidecar: subject, recipients, etc.
```

## Pending vs History

- **Pending** — everything not yet synced. Scrollable list with tap-to-play,
  edit, and per-row delete (confirm dialog).
- **History** — a stats dashboard across *all* captures (pending +
  synced). Stat tiles (count, voice/video split, total time, average,
  longest, shortest), a 12-month stacked bar timeline (voice on top in
  primary colour, video below in secondary), per-recipient counts, top-10
  tag counts. No list — use Pending (or the rendered browser site) to
  drill into individual entries.

## Recipients

The repo ships with generic placeholders (`partner`, `child`, `family`,
`friends`, `general`) — customise before your first real recording.

- Edit `app/src/main/assets/recipients.json` (id, label, emoji) and
  reinstall, **or**
- Manage them at runtime via the gear icon on Home (`Settings →
  Recipients`).

Entries are tagged verbatim with whatever you pick. Tagging an entry
`family` produces `recipients: [family]` in the sidecar, not a smeared
list of every individual person. Alias-based navigation (e.g. "all
entries touching Alice, whether tagged `family` or `alice`") is a
planned ingest-side feature.

## On-device transcription (optional)

Tap the **Transcribe** button on a Pending entry to run Whisper
`tiny.en` locally on the phone. First use downloads the ~78 MB model
over your enabled Network; subsequent runs are offline. The transcript
is saved to `transcriptDraft` in the sidecar.

The ingest pipeline **always re-transcribes server-side with a larger
model** (`small.en` by default). The phone-side `transcriptDraft`
survives in the sidecar as `phone_transcript` for reference but is not
used as the authoritative text or search target.

If your device doesn't have network (or you don't want to enable it),
skip phone-side Transcribe entirely. Server-side transcription gives
better quality anyway.

## Background recording

If you background the app mid-recording it keeps going, with a visible
foreground-service notification. Voice uses
`FOREGROUND_SERVICE_TYPE_MICROPHONE`, video adds
`FOREGROUND_SERVICE_TYPE_CAMERA`. Mismatches here are a known
Android-14+ insta-kill; if you see a restart loop after recording on an
older build, upgrade to `v0.2.0` or later.

## Removing processed entries from the phone

By default, successfully synced entries stay on the phone until the
ingest pipeline's quota policy evicts them (default: inbox capped at
5 GB, oldest-first eviction). Other policies are available — see
[Ingest pipeline → Cleanup modes](ingest.md#cleanup-modes).

You can always manually delete an entry from the Pending screen — the
per-row trash icon removes both the sidecar and the media file.
