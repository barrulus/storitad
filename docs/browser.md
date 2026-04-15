# Browser experience

The rendered static site lives at `~/journal/site/` and is regenerated
from Markdown on every pull. It's plain HTML + one CSS file + ~50 lines
of vanilla JS — no framework, no build step, no external requests.

## Opening it

Any of:

- `nix run .#` opens it automatically in your default browser.
- Open `~/journal/site/index.html` by hand (any browser, any OS).
- Run `storitad-pull serve` for a local HTTP server (see [Server
  mode](server.md)).
- Copy the whole `site/` folder to a USB stick, rsync it to another
  machine, or self-host it behind any web server.

## Pages

### `index.html` — entries

Chronological list of entries newest-first, grouped by month.

- **Search** (top of page): substring match over subject + transcript +
  notes + tags. All client-side against `search-index.json` (≤ 2 KB per
  entry, lowercased).
- **Recipient filters**: chips across the top derived from what you've
  actually tagged. Click to filter; click again to clear.
- Each row links to its detail page.

### Entry detail — `entries/YYYY/MM/<basename>/index.html`

- Inline `<audio>` or `<video>` player.
- Transcript (server-generated, `whisper-small.en` by default).
- Notes section, if any.
- Phone transcript (reference-only, tiny.en), if the phone transcribed it.
- Footer metadata: recipients, mood, tags, GPS (lat/long), device,
  capture timestamp, model used.

### `stats.html` — timeline & stats

Always rendered, even in pure-static mode. Inline SVG, no JS dependency.

- Stat tiles: total, voice, video, total duration, average, longest,
  shortest.
- **Entries per month**: stacked bar chart across the last 12 months.
  Voice in the primary colour (top of each bar), video in the secondary
  colour (bottom).
- Recipients list with counts, sorted by frequency.
- Top 10 tags with counts.

Linked from the `Entries`/`Stats` nav at the top of both pages.

## Sharing / self-hosting

The site is entirely self-contained under `~/journal/site/`. Everything
uses relative paths. No external fonts, no CDN requests, no cookies.

- **USB stick**: `rsync -a ~/journal/site/ /mnt/usb/storitad/`. Double-
  click `index.html` on any machine.
- **Web server**: point nginx / Caddy / whatever at the directory. All
  paths are relative; no rewrite rules needed.
- **GitHub Pages / Netlify / S3**: `site/` is a drop-in static upload.

Heads up if you publish: media files and transcripts *are* inside
`site/`. If you want a filtered public subset, write that tooling
yourself — the ingest doesn't do selective publishing yet.

## Editing from the browser

The static site is read-only. For in-browser edit/delete, run the
optional local server:

```bash
storitad-pull serve --edit
```

See [Server mode](server.md).

## Limitations

- Search is substring-only, case-insensitive, capped at 2 KB of text per
  entry. Good enough for a personal archive; not a full-text engine.
- No pagination. With thousands of entries the index page gets long;
  not yet a problem in practice.
- No timeline scrubbing or transcript-synced playback. Media is just a
  browser-native `<audio>`/`<video>` element.
