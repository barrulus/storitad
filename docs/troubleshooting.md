# Troubleshooting

## Phone

### Phone restarts in a loop after a voice recording
Fixed in `v0.2.0`. Symptom on older builds: Android 14+ kills the app
when the foreground-service type declared at `startForeground` doesn't
match the runtime permissions granted. Upgrade, or check logcat for the
exact type mismatch. Voice uses `FOREGROUND_SERVICE_TYPE_MICROPHONE`;
video adds `FOREGROUND_SERVICE_TYPE_CAMERA`.

### Transcribe button fails with a network error
GrapheneOS blocks network for sideloaded apps by default. Either flip
`Settings → Apps → Storitad → Network` to on, or skip phone-side
transcription entirely (server-side is better anyway).

### "Whisper model SHA-256 mismatch"
The phone verifies the downloaded `ggml-tiny.en.bin` against a
hardcoded SHA. If upstream ever rotates the file, update
`app/src/main/java/uk/storitad/capture/whisper/ModelManager.kt` with
the new hash and rebuild.

### Pending count never decreases after a sync
Your `cleanup.mode` needs to be set and the phone needs to see the
writeback. `quota` and `remove_media` push `processed=true` back; the
phone's Pending list filters on that. If you're on `remove_all`, the
files are gone from the phone entirely — Pending clears because there's
nothing left.

If you're stuck on an old build that pre-dates writeback, just clear
the inbox manually:
```bash
adb shell rm -f '/sdcard/Android/data/uk.storitad.capture/files/Documents/Storitad/inbox/*'
```

## Ingest / desktop

### `adb devices` shows nothing
GrapheneOS: `Settings → About phone` → tap **Build number** 7 times →
`Settings → System → Developer options` → **USB debugging**. Then unplug
and replug the cable; accept the RSA fingerprint prompt on the phone.

### `adb pull` is empty even though Pending shows entries
Paths matter. The app writes to
`/sdcard/Android/data/uk.storitad.capture/files/Documents/Storitad/inbox/`.
Check it directly:

```bash
adb shell ls -la /sdcard/Android/data/uk.storitad.capture/files/Documents/Storitad/inbox/
```

If the path is wrong, something's gone sideways with the package id.

### Transcription fails: `whisper-cli: command not found`
Outside the Nix devshell? Either enter `nix develop`, or install
`whisper.cpp` and put `whisper-cli` on `$PATH`. The ingest shells out
by name (configurable via `whisper_bin` in `config.yml`).

### Transcription fails: model file missing
Check `whisper_model` in config.yml points at the actual path. The
default `~/models/ggml-small.en.bin` isn't fetched for you — see
[Whisper](whisper.md#server-side-ingest--authoritative) for the curl
command.

### Video ingest fails with ffmpeg not found
`ffmpeg` must be on `$PATH`. The Nix flake provides `ffmpeg-headless`;
manual installs need their own. Voice-only ingest does not need it.

### `compileSdk 35` errors in Gradle
AndroidX 1.15+ requires API 35. Don't downgrade. The flake pins SDK 35
and `aapt2`; manual installs need Android SDK 35 installed.

### Site renders but search does nothing
Check `site/search-index.json` exists and isn't 0 bytes. It's
regenerated every run; a zero-byte file means the render crashed
mid-way. Run `storitad-pull pull --rerender` and watch stderr.

### Phone inbox never shrinks under `quota`
`quota` only evicts items that are *already synced*, tracked by the
presence of their sidecar under `~/journal/processed/`. If nothing is
under there, nothing to evict — you haven't run a successful sync yet.

Also: `quota_gb` is the *phone* inbox size cap, measured via
`adb shell ls -la`, not your archive size. If the phone only has 3 GB
of recordings, a 5 GB quota won't trigger any eviction.

## Server mode

### `storitad-pull serve` refuses to bind
Port in use. Either stop the other process, or pass `--port N` with a
free port.

### Edit / Delete buttons don't appear
The site has to be rendered with `edit_mode=True`. The `serve --edit`
command re-renders on startup and on every mutation, so:

1. Make sure you started with `--edit`.
2. Hard-refresh the browser (cached HTML from a previous static render
   has no buttons).

### Edits disappear after next `storitad-pull`
That shouldn't happen — the server writes to the `.md` source. If a
subsequent re-ingest is blowing away your edits, you probably left the
staging copy of the sidecar lying around and a re-run of `pull` is
re-processing it. Clear `<archive>/.staging/` and try again.

## General

### "something went wrong" — where do I look?
1. Pull logs: stderr of `storitad-pull` (wraps `adb`, `ffmpeg`,
   `whisper-cli`).
2. Phone logs: `adb logcat -T 1h | grep -i storitad` (last hour).
3. Archive state: `tree -L 3 ~/journal/`.
4. Staging debris: `ls -la ~/journal/.staging/`.

If an issue reproduces reliably, open a GitHub issue with the exact
command, the full output, and the structure of `~/journal/.staging/`
at the point of failure.
