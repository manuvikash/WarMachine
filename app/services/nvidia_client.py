"""Thin wrapper around NVIDIA NIM API for vision and text models."""

from __future__ import annotations

import base64
import io
import json
from typing import TYPE_CHECKING

import httpx
from openai import AsyncOpenAI
from PIL import Image

from app.config import Settings, get_settings
from app.services.prompts import TTS_SUMMARIZE_SYSTEM

if TYPE_CHECKING:
    from fastapi import UploadFile

_INVOKE_URL = "https://integrate.api.nvidia.com/v1/chat/completions"


def _get_text_client(settings: Settings | None = None) -> AsyncOpenAI:
    settings = settings or get_settings()
    return AsyncOpenAI(
        base_url=settings.nvidia_base_url,
        api_key=settings.nvidia_text_api_key,
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
    temperature: float = 0.15,
) -> str:
    """Send an image + text to the vision model via raw HTTP (streaming)."""
    settings = settings or get_settings()
    data_uri = await _encode_upload(image_file)

    headers = {
        "Authorization": f"Bearer {settings.nvidia_vision_api_key}",
        "Accept": "text/event-stream",
    }

    payload = {
        "model": settings.vision_model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user_prompt},
                    {"type": "image_url", "image_url": {"url": data_uri}},
                ],
            },
        ],
        "max_tokens": max_tokens,
        "temperature": temperature,
        "top_p": 1.00,
        "frequency_penalty": 0.00,
        "presence_penalty": 0.00,
        "stream": True,
    }

    collected: list[str] = []
    async with httpx.AsyncClient(timeout=120.0) as client:
        async with client.stream("POST", _INVOKE_URL, headers=headers, json=payload) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                if not line or not line.startswith("data: "):
                    continue
                data = line.removeprefix("data: ").strip()
                if data == "[DONE]":
                    break
                chunk = json.loads(data)
                choices = chunk.get("choices", [])
                if not choices:
                    continue
                delta = choices[0].get("delta", {})
                if content := delta.get("content"):
                    collected.append(content)

    return "".join(collected)


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
    client = _get_text_client(settings)

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


async def generate_tts_summary(detailed_response: str, *, settings: Settings | None = None) -> str:
    """Generate a short, spoken-friendly summary of a detailed LLM response."""
    try:
        summary = await text_chat(
            TTS_SUMMARIZE_SYSTEM,
            f"Summarize this for voice narration:\n\n{detailed_response}",
            settings=settings,
            max_tokens=256,
            temperature=0.3,
        )
        return summary.strip()
    except Exception as e:
        print(f"[tts_summary] Failed to generate summary: {e}")
        return ""
