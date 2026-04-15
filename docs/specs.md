# Specs

The original design documents live at the repo root in Emacs org-mode.
They are plain text and readable in any editor.

- [`storitad-spec.org`](../storitad-spec.org) — Android app. Phases
  1–3 shipped; 4+ parked.
- [`ingest-spec.org`](../ingest-spec.org) — sixseven (desktop) pipeline.
  Phase 1 shipped, Phase 2 partially landed.

The specs are the project's historical record — decisions, rationale,
phase boundaries. The `docs/` guides you're reading now are the living
user-facing documentation; when they disagree with the specs, the docs
are right. The specs are preserved so that design context isn't lost.

## Shipped vs planned

### Phone app
- ✅ Phase 1: voice capture, sidecars, Pending / History
- ✅ Phase 2: video (CameraX), pause/resume, waveform, configurable
  recipients
- ✅ Phase 3: on-demand tiny.en transcription, GPS opt-in per entry,
  timeline-stats History dashboard
- ⏸️ Phase 4+: speaker diarisation, on-device search, etc.

### Ingest
- ✅ Phase 1: adb pull, sidecar v1+v2, server-side `small.en`
  transcription, Markdown + static HTML render
- ✅ Phase 2 (partial): `processed=true` writeback, phone cleanup
  policy (quota / remove_all / remove_media), local server with
  in-browser edit/delete, `stats.html` dashboard
- ⏸️ Still to ship in Phase 2: mtp transport, `--fetch-model` with
  SHA verify, `--retranscribe` flag, PDF export
- ⏸️ Phase 3: reverse geocoding, video thumbnails, diff-based rebuild,
  Atom feed, speaker diarisation

## Out of scope (design decision)

- Cloud sync
- Multi-user / ACLs
- Comments / social features
- Selective publishing (the whole `site/` is all-or-nothing)
