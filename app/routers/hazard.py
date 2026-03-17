"""POST /hazard  —  Image → hazard detection (Cosmos-Reason2-8B)."""

from __future__ import annotations

from fastapi import APIRouter, File, HTTPException, UploadFile

from app.models.schemas import HazardDetectionResponse
from app.services.nvidia_client import generate_tts_summary, vision_chat
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
    tts = await generate_tts_summary(raw)
    return HazardDetectionResponse(response=raw, tts_summary=tts)
