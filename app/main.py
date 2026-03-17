"""Emergency Response API — powered by NVIDIA NIM."""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.config import get_settings
from app.history import get_all as get_history, IMAGES_DIR
from app.models.schemas import HealthResponse
from app.rag.vector_store import ingest_documents
from app.routers import first_aid, hazard, survival_rag


@asynccontextmanager
async def lifespan(app: FastAPI):
    n = ingest_documents()
    print(f"[startup] Ingested {n} survival-guide chunks into ChromaDB")
    yield


app = FastAPI(
    title="Emergency Response API",
    description=(
        "Three-endpoint backend for disaster / emergency scenarios.\n\n"
        "* **First Aid** — upload an image, receive first-aid guidance "
        "(Cosmos-Reason2-8B)\n"
        "* **Hazard Detection** — upload an image, receive hazard analysis "
        "(Cosmos-Reason2-8B)\n"
        "* **Survival RAG** — ask a question, get answers grounded in a "
        "survival guide (Nemotron-3-Super-120B)"
    ),
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(first_aid.router)
app.include_router(hazard.router)
app.include_router(survival_rag.router)


IMAGES_DIR.mkdir(exist_ok=True)
app.mount("/images", StaticFiles(directory=str(IMAGES_DIR)), name="images")


@app.get("/history", tags=["History"])
async def list_history():
    return get_history()


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    settings = get_settings()
    return HealthResponse(
        status="ok",
        vision_model=settings.vision_model,
        text_model=settings.text_model,
    )
