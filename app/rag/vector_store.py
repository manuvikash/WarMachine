"""ChromaDB-backed vector store for the survival-guide RAG pipeline."""

from __future__ import annotations

import os
from pathlib import Path

import chromadb
import fitz  # PyMuPDF
from chromadb.utils.embedding_functions import SentenceTransformerEmbeddingFunction

from app.config import get_settings

_COLLECTION_NAME = "survival_guide"

_embedding_fn = SentenceTransformerEmbeddingFunction(
    model_name="all-MiniLM-L6-v2",
)


def _get_collection() -> chromadb.Collection:
    settings = get_settings()
    client = chromadb.PersistentClient(path=settings.chroma_persist_dir)
    return client.get_or_create_collection(
        name=_COLLECTION_NAME,
        embedding_function=_embedding_fn,
    )


def _read_pdf(filepath: Path) -> str:
    """Extract text from a PDF using PyMuPDF."""
    doc = fitz.open(filepath)
    pages = [page.get_text() for page in doc]
    doc.close()
    return "\n".join(pages)


def ingest_documents(directories: list[str | Path] | None = None) -> int:
    """Read .txt/.md/.pdf files from *directories* and upsert into ChromaDB.

    Returns the number of chunks ingested.
    """
    if directories is None:
        project_root = Path(__file__).parent.parent.parent
        directories = [
            Path(__file__).parent / "documents",
            project_root / "war_survival_docs",
        ]

    collection = _get_collection()
    chunks: list[str] = []
    ids: list[str] = []
    metadatas: list[dict] = []

    for directory in directories:
        directory = Path(directory)
        if not directory.exists():
            print(f"[ingest] Skipping missing directory: {directory}")
            continue
        for filepath in sorted(directory.glob("*")):
            if filepath.suffix in {".txt", ".md"}:
                text = filepath.read_text(encoding="utf-8")
            elif filepath.suffix == ".pdf":
                text = _read_pdf(filepath)
            else:
                continue

            if not text.strip():
                print(f"[ingest] Skipping empty file: {filepath.name}")
                continue

            file_chunks = _chunk_text(text, chunk_size=500, overlap=50)
            for i, chunk in enumerate(file_chunks):
                doc_id = f"{filepath.stem}__chunk_{i}"
                chunks.append(chunk)
                ids.append(doc_id)
                metadatas.append({"source": filepath.name, "chunk_index": i})

    if not chunks:
        return 0

    collection.upsert(ids=ids, documents=chunks, metadatas=metadatas)
    return len(chunks)


def query(question: str, n_results: int = 5) -> list[dict]:
    """Return the top-k most relevant chunks for *question*."""
    collection = _get_collection()
    if collection.count() == 0:
        return []

    results = collection.query(query_texts=[question], n_results=n_results)

    output = []
    for doc, meta, dist in zip(
        results["documents"][0],
        results["metadatas"][0],
        results["distances"][0],
    ):
        output.append(
            {
                "text": doc,
                "source": meta.get("source", ""),
                "relevance_score": round(1 - dist, 4),
            }
        )
    return output


def _chunk_text(text: str, chunk_size: int = 500, overlap: int = 50) -> list[str]:
    """Naïve word-level chunker with overlap."""
    words = text.split()
    chunks = []
    start = 0
    while start < len(words):
        end = start + chunk_size
        chunks.append(" ".join(words[start:end]))
        start += chunk_size - overlap
    return chunks
