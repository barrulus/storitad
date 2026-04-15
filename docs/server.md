# Server mode

`storitad-pull serve` runs a tiny local HTTP server on top of the
rendered `site/` directory. Two reasons to use it:

1. You want the site served over `http://` instead of `file://` (some
   browsers are pickier about `file://` for media and JS).
2. You want to **edit or delete entries from inside the browser**
   (`--edit`).

## Running

```bash
storitad-pull serve                  # read-only, port 8765
storitad-pull serve --port 9000      # different port
storitad-pull serve --edit           # enable edit + delete buttons
storitad-pull serve --no-open        # don't open the browser automatically
```

The server always re-renders the site before binding, so the edit-mode
UI reflects the current flag.

## What `--edit` gives you

A row of **Edit** and **Delete** buttons at the top of each entry
detail page.

- **Delete**: confirmation dialog, then `DELETE /api/entry/<id>`. The
  source `.md` and media file are removed, the rendered entry directory
  is blown away, and the site is re-rendered. Browser returns to the
  index.
- **Edit**: the title, notes, and tags fields become inline inputs.
  Hit Save; `PATCH /api/entry/<id>` rewrites the frontmatter (subject,
  tags) and the `## Notes` section (body), then re-renders the site.
  Browser reloads.

Edit controls are only injected into the HTML when the site was last
rendered with `edit_mode=True`. If you later run `storitad-pull pull`,
the re-render drops them — the static site is always safe to share.

## Security model

**Bound to `127.0.0.1` only. No authentication. By design.**

This server lets any caller reachable over the network rewrite or
delete files on your disk. The hard rule is therefore: **never bind to
a non-loopback address**. The server refuses to consider it; the code
passes `"127.0.0.1"` to `ThreadingHTTPServer` directly.

If you want to reach the UI from a phone or tablet on your LAN, do it
over an SSH tunnel:

```bash
ssh -L 8765:127.0.0.1:8765 laptop
# then visit http://localhost:8765 on the machine you SSH'd from
```

Do not put this behind nginx and expose it to the internet. If you want
shared editing, you want a different product.

## API reference

Only active when `--edit` is set. All endpoints assume loopback only.

### `DELETE /api/entry/<id>`

Removes the entry. Status:

- `204 No Content` — deleted
- `404 Not Found` — no md file with that id/basename
- `405 Method Not Allowed` — edit mode not enabled
- `500` — filesystem error

### `PATCH /api/entry/<id>`

Body (JSON, all fields optional):

```json
{
  "subject": "New title",
  "notes":   "Replacement notes, or empty string to drop the section",
  "tags":    ["tag1", "tag2"]
}
```

Writes to the source `.md` frontmatter and body, re-renders site.
`204 No Content` on success.

## Limitations

- Edits are blind-overwrite — no merge, no history, no undo. The source
  md is in git if you `git init` the archive root; otherwise your file
  manager's trash is your only safety net.
- Media cannot be re-uploaded or replaced via the API. If you need to
  swap the media, edit the `.md` and drop a replacement file next to it
  by hand, then `storitad-pull --rerender`.
- The server is single-process; do not run two instances against the
  same archive.
