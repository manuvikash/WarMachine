"""POST /first-aid  —  Image → first-aid advice (Cosmos-Reason2-8B)."""

from __future__ import annotations

import re

from fastapi import APIRouter, File, HTTPException, UploadFile

from app.models.schemas import FirstAidResponse
from app.services.nvidia_client import vision_chat
from app.services.prompts import FIRST_AID_SYSTEM, FIRST_AID_USER

router = APIRouter(prefix="/first-aid", tags=["First Aid"])

_ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp"}


@router.post("/", response_model=FirstAidResponse)
async def analyse_first_aid(image: UploadFile = File(...)):
    if image.content_type not in _ALLOWED_TYPES:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported image type '{image.content_type}'. Use JPEG, PNG, or WebP.",
        )

    raw = await vision_chat(image, FIRST_AID_SYSTEM, FIRST_AID_USER)
    return _parse_first_aid_response(raw)


def _parse_first_aid_response(raw: str) -> FirstAidResponse:
    """Best-effort structured parse of the LLM output."""
    assessment = _extract_section(raw, "ASSESSMENT") or raw[:200]
    severity = _extract_section(raw, "SEVERITY") or "unknown"
    steps_block = _extract_section(raw, "STEPS") or ""
    steps = [s.strip() for s in re.split(r"\d+\.\s*", steps_block) if s.strip()]
    seek_help_text = _extract_section(raw, "SEEK PROFESSIONAL HELP") or ""
    seek_help = "yes" in seek_help_text.lower()

    return FirstAidResponse(
        injury_assessment=assessment,
        first_aid_steps=steps or [raw],
        severity=severity.lower().strip(),
        seek_professional_help=seek_help,
        raw_llm_response=raw,
    )


def _extract_section(text: str, header: str) -> str | None:
    pattern = rf"{header}\s*:\s*(.*?)(?=\n[A-Z _]+:|$)"
    match = re.search(pattern, text, re.DOTALL | re.IGNORECASE)
    return match.group(1).strip() if match else None
