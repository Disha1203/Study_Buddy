"""
Study Buddy — Relevance API  (fixed)
=====================================
Minimal FastAPI service for semantic similarity scoring.

Only two responsibilities (SOLID SRP):
  1. Generate sentence embeddings via sentence-transformers
  2. Return a calibrated cosine similarity score

══════════════════════════════════════════════════════════════
 BUG FIX — Scoring normalization was wrong
══════════════════════════════════════════════════════════════

 ORIGINAL (broken):
   raw_score = cosine_similarity(topic, content)   # range: [-1, 1]
   score = (raw_score + 1.0) / 2.0                # maps to [0, 1]

 WHY IT WAS WRONG:
   all-MiniLM-L6-v2 produces cosine similarities that, for English text,
   almost never go negative. Real-world scores cluster tightly in [0.1, 0.9].
   Applying the (x+1)/2 shift compresses the useful range into [0.55, 0.95],
   which means:
     - Completely unrelated content ("watermelon" vs "malware analysis")
       → raw ≈ 0.13 → normalized to 0.565  (above borderline threshold!)
     - Genuinely relevant content ("PE header analysis")
       → raw ≈ 0.60 → normalized to 0.80   (correct)
     - The borderline zone [0.40, 0.65] that Java uses for classification
       is therefore never hit by truly unrelated content — everything
       compresses up into it.

 FIX — Use raw cosine similarity directly, clamped to [0, 1]:
   score = max(0.0, raw_score)

   This preserves the natural distribution of the model:
     - Unrelated text:   raw ≈ 0.10–0.20  → score 0.10–0.20  (BLOCKED  ✓)
     - Borderline text:  raw ≈ 0.40–0.65  → score 0.40–0.65  (BORDERLINE → BLOCKED by Java ✓)
     - Relevant text:    raw ≈ 0.65–0.90  → score 0.65–0.90  (ALLOWED  ✓)

   Negative cosine values (near-zero noise) are clamped to 0.0 — they mean
   "completely unrelated", so a score of 0 is correct.

 ADDITIONALLY — Thresholds recalibrated in Java (RelevanceService):
   With raw scores the Java thresholds need a small adjustment:
     Old (distorted): ALLOWED >= 0.65 | BORDERLINE 0.40-0.65 | BLOCKED < 0.40
     New (raw):       ALLOWED >= 0.55 | BORDERLINE 0.35-0.55 | BLOCKED < 0.35
   See RelevanceService.java for the updated constants.

══════════════════════════════════════════════════════════════

Install:
  pip install fastapi uvicorn sentence-transformers

Run:
  uvicorn relevance_api:app --host 0.0.0.0 --port 8001
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer, util

app = FastAPI(title="Study Buddy Relevance API", version="1.1.0")

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

    Score interpretation (thresholds enforced on Java side):
      >= 0.55  →  ALLOWED      (clearly on-topic)
      0.35–0.55 → BORDERLINE  (uncertain — Java promotes to BLOCKED)
      < 0.35   →  BLOCKED      (off-topic)
    """
    if not req.topic.strip() or not req.content.strip():
        raise HTTPException(
            status_code=400,
            detail="topic and content must not be empty"
        )

    # Encode both texts — model handles tokenisation internally
    topic_embedding   = model.encode(req.topic,   convert_to_tensor=True)
    content_embedding = model.encode(req.content, convert_to_tensor=True)

    # Raw cosine similarity in [-1, 1]
    raw_score: float = util.cos_sim(topic_embedding, content_embedding).item()

    # FIX: Use raw score directly, clamped to [0, 1].
    # DO NOT apply (raw + 1) / 2 — that compresses the distribution and
    # makes unrelated content score 0.5+ (above the block threshold).
    score = max(0.0, min(1.0, raw_score))

    return RelevanceResponse(score=round(score, 4))


# ── Health check ──────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME, "version": "1.1.0"}


# ── Manual test endpoint (useful for calibration) ─────────────────────────────

@app.post("/debug")
def debug_relevance(req: RelevanceRequest):
    """
    Returns both the raw and normalized scores for calibration/debugging.
    Remove or secure this endpoint in production.
    """
    if not req.topic.strip() or not req.content.strip():
        raise HTTPException(status_code=400, detail="topic and content must not be empty")

    topic_embedding   = model.encode(req.topic,   convert_to_tensor=True)
    content_embedding = model.encode(req.content, convert_to_tensor=True)
    raw_score: float  = util.cos_sim(topic_embedding, content_embedding).item()

    old_formula = max(0.0, min(1.0, (raw_score + 1.0) / 2.0))
    new_formula = max(0.0, min(1.0, raw_score))

    return {
        "topic":       req.topic,
        "content":     req.content[:80] + ("..." if len(req.content) > 80 else ""),
        "raw_cosine":  round(raw_score, 4),
        "old_score":   round(old_formula, 4),   # (raw+1)/2  ← was wrong
        "new_score":   round(new_formula, 4),   # raw clamped ← correct
        "verdict_old": _verdict(old_formula, 0.65, 0.40),
        "verdict_new": _verdict(new_formula, 0.55, 0.35),
    }


def _verdict(score: float, allow_threshold: float, block_threshold: float) -> str:
    if score >= allow_threshold:
        return "ALLOWED"
    if score >= block_threshold:
        return "BORDERLINE"
    return "BLOCKED"