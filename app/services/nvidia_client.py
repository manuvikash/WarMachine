"""Thin wrapper around the OpenAI SDK pointed at NVIDIA NIM."""

from __future__ import annotations

import base64
import io
from typing import TYPE_CHECKING

from openai import AsyncOpenAI
from PIL import Image

from app.config import Settings, get_settings

if TYPE_CHECKING:
    from fastapi import UploadFile


def _get_client(settings: Settings | None = None) -> AsyncOpenAI:
    settings = settings or get_settings()
    return AsyncOpenAI(
        base_url=settings.nvidia_base_url,
        api_key=settings.nvidia_api_key,
    )


async def _encode_upload(file: UploadFile) -> str:
    """Read an UploadFile and return a data-URI string (base64)."""
    raw = await file.read()
    img = Image.open(io.BytesIO(raw))
    fmt = img.format or "JPEG"
    mime = f"image/{fmt.lower()}"

    buf = io.BytesIO()
    img.save(buf, format=fmt)
    b64 = base64.b64encode(buf.getvalue()).decode()
    return f"data:{mime};base64,{b64}"


async def vision_chat(
    image_file: UploadFile,
    system_prompt: str,
    user_prompt: str,
    *,
    settings: Settings | None = None,
    max_tokens: int = 2048,
    temperature: float = 0.2,
) -> str:
    """Send an image + text prompt to the vision model (Cosmos-Reason2-8B)."""
    settings = settings or get_settings()
    client = _get_client(settings)
    data_uri = await _encode_upload(image_file)

    response = await client.chat.completions.create(
        model=settings.vision_model,
        messages=[
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user_prompt},
                    {"type": "image_url", "image_url": {"url": data_uri}},
                ],
            },
        ],
        max_tokens=max_tokens,
        temperature=temperature,
    )
    return response.choices[0].message.content or ""


async def text_chat(
    system_prompt: str,
    user_prompt: str,
    *,
    settings: Settings | None = None,
    max_tokens: int = 2048,
    temperature: float = 0.3,
) -> str:
    """Send a text-only prompt to the text model (Nemotron-3-Super)."""
    settings = settings or get_settings()
    client = _get_client(settings)

    response = await client.chat.completions.create(
        model=settings.text_model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        max_tokens=max_tokens,
        temperature=temperature,
    )
    return response.choices[0].message.content or ""
