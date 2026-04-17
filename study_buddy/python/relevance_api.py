"""
Study Buddy — Relevance API
===========================
Minimal FastAPI service for semantic similarity scoring.

Only two responsibilities (SOLID SRP):
  1. Generate sentence embeddings via sentence-transformers
  2. Return cosine similarity score

Install:
  pip install fastapi uvicorn sentence-transformers

Run:
  uvicorn relevance_api:app --host 0.0.0.0 --port 8001
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer, util
import torch

app = FastAPI(title="Study Buddy Relevance API", version="1.0.0")

# Load model once at startup — lightweight & fast for CPU
# "all-MiniLM-L6-v2" is 80 MB and produces 384-dim embeddings
MODEL_NAME = "all-MiniLM-L6-v2"
model = SentenceTransformer(MODEL_NAME)


# ── Request / Response schemas ────────────────────────────────────────────────

class RelevanceRequest(BaseModel):
    topic: str
    content: str


class RelevanceResponse(BaseModel):
    score: float


# ── Endpoint ──────────────────────────────────────────────────────────────────

@app.post("/relevance", response_model=RelevanceResponse)
def check_relevance(req: RelevanceRequest) -> RelevanceResponse:
    """
    Returns a cosine similarity score in [0.0, 1.0] between the
    study topic and the page content.
    """
    if not req.topic.strip() or not req.content.strip():
        raise HTTPException(status_code=400, detail="topic and content must not be empty")

    # Encode both texts — model handles tokenisation internally
    topic_embedding   = model.encode(req.topic,   convert_to_tensor=True)
    content_embedding = model.encode(req.content, convert_to_tensor=True)

    # Cosine similarity is in [-1, 1]; clamp to [0, 1] for a clean score
    raw_score = util.cos_sim(topic_embedding, content_embedding).item()
    score = max(0.0, min(1.0, (raw_score + 1.0) / 2.0))  # normalise to [0,1]

    return RelevanceResponse(score=round(score, 4))


# ── Health check ──────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}
