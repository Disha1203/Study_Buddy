"""
Study Buddy — Relevance API  (v1.5.0 FINAL)
===========================================

FINAL IMPROVEMENTS:
✔ Switched to mxbai-embed-large-v1 (better than MPNet)
✔ Correct asymmetric encoding (query vs passage)
✔ Cached embeddings (fast)
✔ Safer preprocessing
✔ Noise penalty
✔ Robust error handling
"""

import re
from functools import lru_cache
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer, util

app = FastAPI(title="Study Buddy Relevance API", version="1.5.0")

# ── MODEL ─────────────────────────────────────────────────────────────

MODEL_NAME = "mixedbread-ai/mxbai-embed-large-v1"
model = SentenceTransformer(MODEL_NAME)

# ── THRESHOLDS ────────────────────────────────────────────────────────

ALLOW_THRESHOLD = 0.55
BLOCK_THRESHOLD = 0.40

# ── LIMITS ────────────────────────────────────────────────────────────

MAX_WORDS = 512
MIN_CLEAN_WORDS = 4
NOISE_PENALTY_WORD_THRESHOLD = 40

# ── BOILERPLATE CLEANING ──────────────────────────────────────────────

BOILERPLATE_PATTERNS = [
    r"we (use|value) (cookies?|your privacy)[^.]{0,120}\.",
    r"(accept|decline) (all )?cookies?[^.]{0,120}\.",
    r"privacy policy[^.]{0,120}\.",
    r"cookie policy[^.]{0,120}\.",

    r"(log ?in|sign ?in|sign ?up|create an account)[^.]{0,120}\.",
    r"(user agreement|terms of (service|use))[^.]{0,120}\.",

    r"page not found[^.]{0,120}\.",
    r"404[^.]{0,120}\.",

    r"sponsored (ad|content|post|link)[^.]{0,120}\.",
    r"advertisement[^.]{0,120}\.",

    r"jump to (content|navigation|search)[^.]{0,120}\.",
    r"(from )?wikipedia, the free encyclopedia[^.]{0,120}\.",

    r"please wait[^.]{0,120}\.",
]

_BOILERPLATE_RE = re.compile("|".join(BOILERPLATE_PATTERNS), flags=re.IGNORECASE)

# ── REQUEST / RESPONSE ─────────────────────────────────────────────────

class RelevanceRequest(BaseModel):
    topic: str
    content: str

class RelevanceResponse(BaseModel):
    score: float

# ── ENCODING (ASYMMETRIC — VERY IMPORTANT) ─────────────────────────────

@lru_cache(maxsize=256)
def encode_topic(text: str):
    return model.encode(
        f"Represent this sentence for searching relevant passages: {text}",
        convert_to_tensor=True
    )

@lru_cache(maxsize=256)
def encode_content(text: str):
    return model.encode(text, convert_to_tensor=True)

# ── HELPERS ───────────────────────────────────────────────────────────

def truncate(text: str) -> str:
    words = text.split()
    return " ".join(words[:MAX_WORDS])

def preprocess(content: str) -> tuple[str, float]:
    original_words = len(content.split())

    cleaned = _BOILERPLATE_RE.sub("", content)
    cleaned = re.sub(r"\s+", " ", cleaned).strip().lower()

    cleaned_words = len(cleaned.split())
    removed = original_words - cleaned_words
    noise_ratio = removed / max(original_words, 1)

    return cleaned, noise_ratio

# ── CORE SCORING ──────────────────────────────────────────────────────

def compute_score(topic: str, content: str) -> float:

    clean_content, noise_ratio = preprocess(content)

    if len(clean_content.split()) < MIN_CLEAN_WORDS:
        return 0.0

    clean_content = truncate(clean_content)

    # 🔥 KEY FIX: asymmetric encoding
    topic_emb = encode_topic(topic)
    content_emb = encode_content(clean_content)

    raw = util.cos_sim(topic_emb, content_emb).item()
    score = max(0.0, min(1.0, raw))

    # ── Noise penalty ────────────────────────────────────────────────
    original_word_count = len(content.split())
    if original_word_count >= NOISE_PENALTY_WORD_THRESHOLD and noise_ratio > 0.3:
        penalty = 1.0 - (noise_ratio - 0.3) * (0.5 / 0.7)
        score *= max(0.5, penalty)

    return max(0.0, min(1.0, score))

# ── API ───────────────────────────────────────────────────────────────

@app.post("/relevance", response_model=RelevanceResponse)
def check_relevance(req: RelevanceRequest):

    topic = req.topic.strip()
    content = req.content.strip()

    if not topic or not content:
        raise HTTPException(
            status_code=400,
            detail="topic and content must not be empty"
        )

    try:
        score = compute_score(topic, content)
        return RelevanceResponse(score=round(score, 4))

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Internal scoring error: {str(e)}"
        )

# ── DEBUG ENDPOINT ────────────────────────────────────────────────────

@app.post("/debug")
def debug(req: RelevanceRequest):

    topic = req.topic.strip()
    content = req.content.strip()

    if not topic or not content:
        raise HTTPException(status_code=400, detail="topic/content empty")

    clean_content, noise_ratio = preprocess(content)

    if len(clean_content.split()) < MIN_CLEAN_WORDS:
        return {
            "cleaned": clean_content,
            "noise_ratio": noise_ratio,
            "score": 0.0,
            "verdict": "BLOCKED"
        }

    topic_emb = encode_topic(topic)
    content_emb = encode_content(clean_content)

    raw = util.cos_sim(topic_emb, content_emb).item()
    final = compute_score(topic, content)

    return {
        "raw": round(raw, 4),
        "final": round(final, 4),
        "noise_ratio": round(noise_ratio, 3),
        "clean_words": len(clean_content.split()),
        "verdict": verdict(final)
    }

# ── VERDICT LOGIC ─────────────────────────────────────────────────────

def verdict(score: float) -> str:
    if score >= ALLOW_THRESHOLD:
        return "ALLOWED"
    if score >= BLOCK_THRESHOLD:
        return "BORDERLINE"
    return "BLOCKED"

# ── HEALTH ────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "version": "1.5.0"
    }