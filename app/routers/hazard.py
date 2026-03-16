"""POST /hazard  —  Image → hazard detection (Cosmos-Reason2-8B)."""

from __future__ import annotations

import re

from fastapi import APIRouter, File, HTTPException, UploadFile

from app.models.schemas import Hazard, HazardDetectionResponse
from app.services.nvidia_client import vision_chat
from app.services.prompts import HAZARD_DETECTION_SYSTEM, HAZARD_DETECTION_USER

router = APIRouter(prefix="/hazard", tags=["Hazard Detection"])

_ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp"}


@router.post("/", response_model=HazardDetectionResponse)
async def detect_hazards(image: UploadFile = File(...)):
    if image.content_type not in _ALLOWED_TYPES:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported image type '{image.content_type}'. Use JPEG, PNG, or WebP.",
        )

    raw = await vision_chat(image, HAZARD_DETECTION_SYSTEM, HAZARD_DETECTION_USER)
    return _parse_hazard_response(raw)


def _parse_hazard_response(raw: str) -> HazardDetectionResponse:
    """Best-effort structured parse of the LLM output."""
    hazards: list[Hazard] = []
    for match in re.finditer(r"-\s*(.+?)\s*\|\s*(.+?)\s*\|\s*(.+)", raw):
        hazards.append(
            Hazard(
                label=match.group(1).strip(),
                confidence=match.group(2).strip().lower(),
                description=match.group(3).strip(),
            )
        )

    risk_match = re.search(
        r"OVERALL RISK\s*:\s*(safe|caution|danger|extreme)",
        raw,
        re.IGNORECASE,
    )
    overall_risk = risk_match.group(1).lower() if risk_match else "unknown"

    actions_block = _extract_section(raw, "RECOMMENDED ACTIONS") or ""
    actions = [a.strip() for a in re.split(r"\d+\.\s*", actions_block) if a.strip()]

    return HazardDetectionResponse(
        hazards_detected=hazards,
        overall_risk_level=overall_risk,
        recommended_actions=actions,
        raw_llm_response=raw,
    )


def _extract_section(text: str, header: str) -> str | None:
    pattern = rf"{header}\s*:\s*(.*?)(?=\n[A-Z _]+:|$)"
    match = re.search(pattern, text, re.DOTALL | re.IGNORECASE)
    return match.group(1).strip() if match else None
