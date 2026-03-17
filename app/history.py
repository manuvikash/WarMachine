"""Persistent request/response history stored as a JSON file + images on disk."""

from __future__ import annotations

import json
import shutil
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from fastapi import UploadFile

IMAGES_DIR = Path("images")
HISTORY_FILE = Path("history.json")


def _ensure_dirs():
    IMAGES_DIR.mkdir(exist_ok=True)


def _load() -> list[dict]:
    if HISTORY_FILE.exists():
        return json.loads(HISTORY_FILE.read_text())
    return []


def _save(entries: list[dict]):
    HISTORY_FILE.write_text(json.dumps(entries, indent=2))


async def save_image(upload: UploadFile) -> str:
    """Save an uploaded image to images/ and return the filename."""
    _ensure_dirs()
    ext = Path(upload.filename or "image.jpg").suffix or ".jpg"
    filename = f"{uuid.uuid4().hex[:12]}{ext}"
    dest = IMAGES_DIR / filename

    await upload.seek(0)
    content = await upload.read()
    dest.write_bytes(content)
    await upload.seek(0)

    return filename


def add_entry(
    *,
    entry_type: str,
    image_filename: str | None = None,
    query: str | None = None,
    response: str,
    tts_summary: str = "",
):
    """Append a history entry to the JSON file."""
    entries = _load()
    entries.insert(0, {
        "id": uuid.uuid4().hex[:12],
        "type": entry_type,
        "image": image_filename,
        "query": query,
        "response": response,
        "tts_summary": tts_summary,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    })
    _save(entries)


def get_all() -> list[dict]:
    """Return all history entries (newest first)."""
    return _load()
