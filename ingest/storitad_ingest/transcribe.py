"""Server-side transcription via whisper.cpp CLI.

Policy (from ingest-spec.org):
- Ingest always transcribes when `transcript` is absent on the markdown.
  The phone's `transcriptDraft` is retained separately as `phone_transcript`.
- Voice entries: whisper.cpp handles m4a natively (via ffmpeg embedded in
  the build), but for portability we always demux to 16 kHz mono WAV first.
- Video entries: ffmpeg demuxes audio track to WAV, then whisper.cpp.
"""
from __future__ import annotations

import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass
class TranscriberConfig:
    whisper_bin: str = "whisper-cli"       # upstream renamed `main` → `whisper-cli` in v1.8
    model_path: str = ""                   # required; set from config
    language: str = "en"
    threads: int = 6
    model_name: str = "whisper-small.en"   # recorded in markdown frontmatter


def _which(bin_: str) -> str | None:
    return shutil.which(bin_)


def _demux_to_wav(media: Path, wav: Path) -> None:
    if _which("ffmpeg") is None:
        raise RuntimeError("ffmpeg not found on $PATH")
    subprocess.check_call([
        "ffmpeg", "-y", "-loglevel", "error",
        "-i", str(media),
        "-vn", "-ac", "1", "-ar", "16000",
        "-f", "wav", str(wav),
    ])


def transcribe(media: Path, cfg: TranscriberConfig) -> str:
    if _which(cfg.whisper_bin) is None:
        raise RuntimeError(
            f"whisper binary {cfg.whisper_bin!r} not found on $PATH — "
            "install whisper-cpp or set whisper_bin in the config"
        )
    if not cfg.model_path or not Path(cfg.model_path).exists():
        raise RuntimeError(
            f"whisper model missing at {cfg.model_path!r} — "
            "set model_path in the config and download it first"
        )

    with tempfile.TemporaryDirectory() as td:
        td_path = Path(td)
        wav = td_path / "audio.wav"
        _demux_to_wav(media, wav)

        # whisper.cpp writes foo.txt next to foo.wav when --output-txt is on.
        subprocess.check_call([
            cfg.whisper_bin,
            "-m", cfg.model_path,
            "-l", cfg.language,
            "-t", str(cfg.threads),
            "-nt",                     # no timestamps in the text output
            "--output-txt",
            "-f", str(wav),
        ])
        txt = wav.with_suffix(".wav.txt")
        if not txt.exists():
            # some whisper.cpp versions strip the .wav
            alt = wav.with_suffix(".txt")
            if alt.exists(): txt = alt
        if not txt.exists():
            raise RuntimeError("whisper did not produce a transcript file")
        return txt.read_text().strip()
