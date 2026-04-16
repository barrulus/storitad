# Installation

Two machines to set up: the **phone** (GrapheneOS) and the **desktop**
that ingests the recordings. You can do just the phone side if you only
want to capture — signed APKs are published on every tagged release and
Obtainium handles install + updates (see [§3](#3-install-the-phone-app)).

## Requirements

### Phone
- GrapheneOS device. Built on Pixel 9 Pro; other Pixels should work.
- Developer options + USB debugging enabled *if* you want to build from
  source or let the desktop ingest pull recordings over USB. Not needed
  for an Obtainium-only install.

### Desktop
- Linux or macOS. Windows is untested; WSL would need [Nix] installed
  inside WSL.
- ~500 MB free disk for the Whisper `small.en` model used for server-side
  transcription.
- Either:
  - [Nix] with flakes enabled (recommended — reproducible toolchain,
    Android SDK + NDK pinned, no ambient dependencies), **or**
  - A manual install with your own Python, `whisper.cpp`, `ffmpeg`, and
    `adb` on `$PATH`.

## 1. Get the source

```bash
git clone https://github.com/barrulus/storitad.git
cd storitad
git submodule update --init --recursive
```

The `whisper.cpp` submodule is required to build the phone app's native
library. The ingest side does not need it (it shells out to a prebuilt
`whisper-cli`).

## 2. Set up the toolchain

### Option A — Nix (recommended)

```bash
nix develop
# or with direnv:
echo 'use flake' > .envrc && direnv allow
```

The devshell provides: pinned Android SDK 35, Gradle, JDK 17, Python
3.11 with `click`/`jinja2`/`pyyaml`/`pytest`, `whisper-cli` (from
`whisper-cpp`), `ffmpeg-headless`, and `adb` (from `android-tools`). No
ambient dependencies.

### Option B — manual install (no Nix)

You need all of the following on `$PATH`:

- Python ≥ 3.11 with `click`, `jinja2`, `pyyaml`
- `whisper-cli` (build it from [`whisper.cpp`], v1.8.4 or newer)
- `ffmpeg` (only needed for video ingest — audio demux)
- `adb` (Android platform-tools)

Then install the ingest in editable mode:

```bash
cd ingest && pip install -e .
```

You won't be able to build the phone app without the Android SDK + NDK;
see [Android SDK below](#android-sdk-for-manual-installs) if you need it.

## 3. Install the phone app

Two paths: **Obtainium** (recommended — handles updates automatically)
or **build from source** (for development).

### Option A — Obtainium (recommended)

You do not need the Nix devshell, the source checkout, or USB debugging
for this path — sections 1 and 2 are only required if you also want to
ingest recordings on a desktop.

1. Install [Obtainium](https://github.com/ImranR98/Obtainium) on the
   phone. Get its APK from its own [GitHub Releases page], or install
   it via the [IzzyOnDroid F-Droid repo].
2. In Obtainium tap **Add App** and paste:
   ```
   https://github.com/barrulus/storitad
   ```
   Obtainium will find the latest signed `storitad-v*.apk` attached to
   that repo's Releases and offer to install it.
3. GrapheneOS will prompt once to allow Obtainium to install unknown
   apps — accept. From then on, Obtainium checks for new Storitad
   releases and installs updates with a single tap.

Releases are cut manually by tagging `vX.Y.Z` on `main`, so updates
arrive when a tag is pushed, not on a fixed cadence. Enable Obtainium's
background update check if you want to be notified.

The release APK is signed with a stable key that will not rotate. If the
signing key ever had to change, you'd need to uninstall and reinstall
(Android refuses upgrades with a new signer) — but that is not planned.

### Option B — build from source

With the Pixel plugged in and USB debugging on:

```bash
./gradlew :app:installDebug
```

The app installs as `Storitad`. Open it, grant mic/camera permissions
when asked, and record something.

### After install (both paths)

**GrapheneOS network note:** sideloaded apps have the per-app *Network*
toggle **off** by default. Only flip it on (`Settings → Apps → Storitad
→ Network`) if you want to use the phone's built-in Transcribe button
(which downloads the Whisper `tiny.en` model once). If you'd rather rely
on server-side `small.en` transcription, leave Network off — the app
works fine.

See [Phone app guide](phone-app.md) for permissions and day-to-day use.

[GitHub Releases page]: https://github.com/ImranR98/Obtainium/releases
[IzzyOnDroid F-Droid repo]: https://apt.izzysoft.de/fdroid/index/apk/dev.imranr.obtainium

## 4. First-run ingest setup

```bash
mkdir -p ~/.config/storitad ~/models
cp ingest/config.example.yml  ~/.config/storitad/config.yml
cp ingest/aliases.example.yml ~/.config/storitad/aliases.yml
```

Edit `~/.config/storitad/config.yml` — at minimum check `archive_root`
(default `~/journal`) and `cleanup.mode` / `cleanup.quota_gb` (defaults
to `quota` at 5 GB; see [Ingest pipeline](ingest.md#cleanup-modes)).

Download the server-side transcription model (one-time, 487 MB):

```bash
curl -L -o ~/models/ggml-small.en.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin
```

See [Whisper / transcription](whisper.md) for model alternatives and SHA
notes.

## 5. First sync

With the Pixel plugged in, USB debugging on, and the Storitad app holding
at least one recording:

```bash
nix run .#
# or manually, if you pip-installed:
storitad-pull
```

This pulls new entries, transcribes them, writes Markdown to
`~/journal/entries/YYYY/MM/`, renders HTML to `~/journal/site/`, and
opens `index.html` in your browser.

See [Ingest pipeline](ingest.md) for day-to-day options and the
[Server mode](server.md) doc if you want in-browser edit/delete.

## Android SDK for manual installs

If you're not using the Nix flake but still want to build the phone app,
install Android Studio (or the `cmdline-tools`) and ensure:

- `compileSdk` **must be 35** — AndroidX 1.15+ forces API 35.
- NDK 26 is required for the native whisper build.
- `ANDROID_HOME` set, `$ANDROID_HOME/platform-tools` on `$PATH`.

The flake does all of this for you; manual install is supported but not
actively tested.

[Nix]: https://nixos.org/download/
[`whisper.cpp`]: https://github.com/ggerganov/whisper.cpp
