from pydantic import BaseModel, Field


# ── First Aid ──────────────────────────────────────────────────────────────────

class FirstAidResponse(BaseModel):
    injury_assessment: str = Field(description="What the model sees in the image")
    first_aid_steps: list[str] = Field(description="Ordered first-aid instructions")
    severity: str = Field(description="Estimated severity: minor / moderate / severe / critical")
    seek_professional_help: bool = Field(description="Whether professional medical help is needed")
    raw_llm_response: str = Field(description="Full model output")


# ── Hazard Detection ──────────────────────────────────────────────────────────

class Hazard(BaseModel):
    label: str = Field(description="Hazard type, e.g. 'structural collapse', 'smoke'")
    confidence: str = Field(description="Confidence level: low / medium / high")
    description: str = Field(description="Brief description of the detected hazard")


class HazardDetectionResponse(BaseModel):
    hazards_detected: list[Hazard] = Field(default_factory=list)
    overall_risk_level: str = Field(description="Overall risk: safe / caution / danger / extreme")
    recommended_actions: list[str] = Field(default_factory=list)
    raw_llm_response: str = Field(description="Full model output")


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


# ── Health check ──────────────────────────────────────────────────────────────

class HealthResponse(BaseModel):
    status: str
    vision_model: str
    text_model: str
