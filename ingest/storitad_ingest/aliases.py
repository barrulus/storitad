"""Recipient alias expansion. Ingest-side only."""
from __future__ import annotations

from pathlib import Path

import yaml


DEFAULT_ALIASES: dict[str, list[str]] = {
    "family": ["griffin", "julian", "casper", "emma"],
}


def load_aliases(path: Path | None) -> dict[str, list[str]]:
    if path is None or not path.exists():
        return dict(DEFAULT_ALIASES)
    data = yaml.safe_load(path.read_text()) or {}
    items = data.get("aliases", {}) if isinstance(data, dict) else {}
    out = dict(DEFAULT_ALIASES)
    for k, v in items.items():
        if isinstance(v, list):
            out[str(k)] = [str(x) for x in v]
    return out


def expand(recipients: list[str], aliases: dict[str, list[str]]) -> list[str]:
    """Keep original ids AND expanded person ids; dedupe preserving order."""
    seen: set[str] = set()
    result: list[str] = []
    for r in recipients:
        if r not in seen:
            seen.add(r); result.append(r)
        for exp in aliases.get(r, []):
            if exp not in seen:
                seen.add(exp); result.append(exp)
    return result
