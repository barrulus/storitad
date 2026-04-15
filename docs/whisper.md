# Whisper / transcription

Storitad transcribes audio with [`whisper.cpp`] — a C++ reimplementation
of OpenAI Whisper that runs on CPU without a PyTorch stack. Two sides
of the system use it independently.

## Server-side (ingest) — authoritative

Every entry is transcribed server-side during `storitad-pull`. Default
model is `whisper-small.en` (~487 MB). The output is written into the
Markdown body under `## Transcript` and into the search index.

Configured in `~/.config/storitad/config.yml`:

```yaml
whisper_bin: whisper-cli
whisper_model: ~/models/ggml-small.en.bin
whisper_model_name: whisper-small.en
```

Video entries get demuxed to 16 kHz mono WAV with `ffmpeg` first, then
fed to `whisper-cli -nt --output-txt`.

### Choosing a model

Downloads from the official upstream mirror:
`https://huggingface.co/ggerganov/whisper.cpp/resolve/main/<name>.bin`

| Model | Size | Quality | Speed (Ryzen 7 / M1) |
|-------|------|---------|-----------------------|
| `ggml-tiny.en.bin` | ~78 MB | rough; fine as phone reference | ~15× realtime |
| `ggml-base.en.bin` | ~148 MB | workable | ~10× realtime |
| `ggml-small.en.bin` (default) | ~487 MB | good | ~5× realtime |
| `ggml-medium.en.bin` | ~1.5 GB | better | ~2× realtime |
| `ggml-large-v3.bin` | ~3.1 GB | best, multilingual | sub-realtime without GPU |

Pick whichever fits your disk and patience. Update both
`whisper_model` (path) and `whisper_model_name` (label written into
frontmatter) when you switch.

### SHA verification

Ingest does **not** currently verify model SHAs — a `--fetch-model`
helper with SHA-256 check is on the roadmap. Verify by hand if you
care:

```bash
sha256sum ~/models/ggml-small.en.bin
# compare against the checksum listed on the whisper.cpp README
```

### Re-transcribing an existing entry

No dedicated flag yet. Workaround: delete the `## Transcript` section
of the `.md` (or the entire file) and re-ingest with
`--entry <basename>` while the sidecar + media are still staged. A
proper `--retranscribe` switch is on the roadmap.

## Phone-side — optional, reference-only

The phone app ships a **Transcribe** button on pending entries. It runs
`tiny.en` (~78 MB) locally on the Pixel via the vendored `whisper.cpp`
submodule, compiled into a native library.

- First use downloads `ggml-tiny.en.bin` once. Requires flipping the
  GrapheneOS per-app Network toggle on for Storitad.
- SHA-256 is verified against a constant in `ModelManager.kt`. If
  upstream ever rotates the file, update that constant.
- Result is saved to the sidecar as `transcriptDraft` (plus
  `transcriptModel`).

The ingest pipeline **always re-transcribes server-side** with the
bigger model. The phone draft survives in the Markdown frontmatter as
`phone_transcript` for reference, and appears in a *Phone transcript
(tiny.en, reference)* section on the entry detail page. It is **not**
searched — server transcript is authoritative.

### Skipping phone-side entirely

Fine. Leave the per-app Network toggle off; never tap Transcribe on
the phone. The system works exactly the same and your phone stays
offline.

### Sideloading the tiny model (if Network stays off)

Download `ggml-tiny.en.bin` on another machine, `adb push` it to the
phone's Documents dir, and use the **Sideload** button in the app's
download dialog. SHA-256 is verified either way.

## Design principle

Verbatim from the spec: *"Heavy lifting on sixseven, not the phone."*
The phone is the capture device; the laptop/desktop is where serious
transcription happens. Anything beyond `tiny.en` on a phone drains
battery too fast and takes too long for the use case.

[`whisper.cpp`]: https://github.com/ggerganov/whisper.cpp
