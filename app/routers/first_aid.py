"""POST /first-aid  —  Image → first-aid advice (Cosmos-Reason2-8B)."""

from __future__ import annotations

from fastapi import APIRouter, File, HTTPException, UploadFile

from app.models.schemas import FirstAidResponse
from app.services.nvidia_client import generate_tts_summary, vision_chat
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
    tts = await generate_tts_summary(raw)
    return FirstAidResponse(response=raw, tts_summary=tts)
