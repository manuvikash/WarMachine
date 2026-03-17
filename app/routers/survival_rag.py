"""POST /survival  —  Text → RAG survival advice (Nemotron-3-Super)."""

from __future__ import annotations

from fastapi import APIRouter

from app.config import get_settings
from app.models.schemas import RetrievedChunk, SurvivalQuery, SurvivalRAGResponse
from app.rag.vector_store import query as vector_query
from app.services.nvidia_client import generate_tts_summary, text_chat
from app.services.prompts import SURVIVAL_RAG_SYSTEM

router = APIRouter(prefix="/survival", tags=["Survival RAG"])


@router.post("/", response_model=SurvivalRAGResponse)
async def ask_survival(body: SurvivalQuery):
    settings = get_settings()
    retrieved = vector_query(body.question, n_results=settings.max_rag_results)

    context_str = "\n\n---\n\n".join(
        f"[{chunk['source']}] {chunk['text']}" for chunk in retrieved
    )
    system_prompt = SURVIVAL_RAG_SYSTEM.format(
        context=context_str or "No relevant documents found."
    )

    raw = await text_chat(system_prompt, body.question)

    sources = [
        RetrievedChunk(
            text=c["text"],
            source=c["source"],
            relevance_score=c["relevance_score"],
        )
        for c in retrieved
    ]

    tts_summary = await generate_tts_summary(raw)

    return SurvivalRAGResponse(
        answer=raw,
        sources=sources,
        raw_llm_response=raw,
        tts_summary=tts_summary,
    )
