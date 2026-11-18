"""Embedding LOCAL via Ollama. Aucun appel réseau sortant autre que localhost."""
import json
import urllib.request

from .config import settings


def _post(path: str, payload: dict) -> dict:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        settings.ollama_url.rstrip("/") + path,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as response:
        return json.loads(response.read().decode("utf-8"))


def embed(texts: list[str]) -> list[list[float]]:
    """Essaie /api/embed (batch) puis /api/embeddings (un texte à la fois).
    VERIFY: endpoints exacts pour la version Ollama installée.
    """
    if not texts:
        return []
    try:
        result = _post("/api/embed", {
            "model": settings.embedding_model,
            "input": texts,
        })
        vectors = result.get("embeddings")
        if vectors:
            return vectors
    except Exception:
        pass

    vectors = []
    for text in texts:
        result = _post("/api/embeddings", {
            "model": settings.embedding_model,
            "prompt": text,
        })
        vectors.append(result["embedding"])
    return vectors


def assert_dim(vector: list[float]) -> None:
    if len(vector) != settings.embedding_dim:
        raise ValueError(
            f"Dimension embedding {len(vector)} != PFE_EMBEDDING_DIM "
            f"{settings.embedding_dim}. Corrige init.sql ET la config AVANT toute ingestion."
        )


def to_pgvector(vector: list[float]) -> str:
    assert_dim(vector)
    return "[" + ",".join(f"{float(x):.7g}" for x in vector) + "]"
