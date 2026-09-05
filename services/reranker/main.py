"""BGE-Reranker 重排序微服务
POST /rerank
{
  "query": "...",
  "passages": [{"id":"chunk_1","text":"...","doc_name":"a.pdf","page_num":3}, ...]
}
-> {"scores":[0.98,0.01,...]}   (与 passages 等序，sigmoid 归一化到 0~1)
"""
import math
import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

MODEL_NAME = os.environ.get("RERANK_MODEL", "BAAI/bge-reranker-base")

app = FastAPI(title="BGE-Reranker", version="1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_model = None


class Passage(BaseModel):
    id: str
    text: str
    doc_name: str = ""
    page_num: int = 0


class RerankRequest(BaseModel):
    query: str
    passages: list[Passage]


def get_model():
    global _model
    if _model is None:
        from sentence_transformers import CrossEncoder

        _model = CrossEncoder(MODEL_NAME, max_length=512)
    return _model


@app.post("/rerank")
def rerank(req: RerankRequest):
    if not req.passages:
        return {"scores": []}
    model = get_model()
    pairs = [(req.query, p.text) for p in req.passages]
    logits = model.predict(pairs, show_progress_bar=False)
    scores = [float(1.0 / (1.0 + math.exp(-x))) for x in logits]
    return {"scores": scores, "model": MODEL_NAME}


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": _model is not None, "model": MODEL_NAME}
