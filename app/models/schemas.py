from pydantic import BaseModel, Field


# ── First Aid ──────────────────────────────────────────────────────────────────

class FirstAidResponse(BaseModel):
    response: str = Field(description="Short plain-text first-aid advice")
    tts_summary: str = Field(default="", description="Concise version for text-to-speech")


# ── Hazard Detection ──────────────────────────────────────────────────────────

class HazardDetectionResponse(BaseModel):
    response: str = Field(description="Short plain-text hazard assessment")
    tts_summary: str = Field(default="", description="Concise version for text-to-speech")


# ── Survival RAG ──────────────────────────────────────────────────────────────

class SurvivalQuery(BaseModel):
    question: str = Field(description="Survival-related question", min_length=3, max_length=2000)


class RetrievedChunk(BaseModel):
    text: str
    source: str = ""
    relevance_score: float = 0.0


class SurvivalRAGResponse(BaseModel):
    answer: str = Field(description="Generated answer grounded in survival guide")
    sources: list[RetrievedChunk] = Field(default_factory=list)
    raw_llm_response: str = Field(description="Full model output")
    tts_summary: str = Field(default="", description="Short concise summary for text-to-speech")


# ── Health check ──────────────────────────────────────────────────────────────

class HealthResponse(BaseModel):
    status: str
    vision_model: str
    text_model: str
