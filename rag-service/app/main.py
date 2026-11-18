import hashlib
import json
from typing import Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from .chunk import chunk_code, chunk_markdown
from .config import settings
from .context import build_context
from .db import connection
from .embed import embed, assert_dim, to_pgvector
from .retrieve import hybrid_search

app = FastAPI(title="RAG service local — AI Software Factory", docs_url="/docs")


class IngestDoc(BaseModel):
    sourceUri: str
    title: str
    kind: Literal["STANDARD", "EXAMPLE_CODE", "ADR", "README", "TEST_TEMPLATE"]
    language: str = "fr"
    version: str = "v1"
    content: str
    filePath: str | None = None


class SearchRequest(BaseModel):
    query: str
    kind: str | None = None
    k: int = Field(default=5, ge=1, le=20)


class ContextRequest(BaseModel):
    targetApp: str
    hints: list[str] = []
    requirementTexts: list[str] = []
    maxTokens: int | None = None


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "ollamaUrl": settings.ollama_url,
        "embeddingModel": settings.embedding_model,
        "embeddingDim": settings.embedding_dim,
        "note": "Local uniquement. Aucun appel externe.",
    }


@app.post("/documents")
def ingest(doc: IngestDoc) -> dict:
    if doc.language not in ("fr", "en"):
        raise HTTPException(400, "langue non supportée")
    chunks = (chunk_code(doc.content, {"source_uri": doc.sourceUri, "file_path": doc.filePath or "",
                                       "language": doc.language, "kind": doc.kind})
              if doc.kind == "EXAMPLE_CODE"
              else chunk_markdown(doc.content, {
                  "source_uri": doc.sourceUri, "language": doc.language, "kind": doc.kind}))
    if not chunks:
        raise HTTPException(400, "aucun chunk produit")

    vectors = embed([c["content"] for c in chunks])
    for vector in vectors:
        assert_dim(vector)  # échoue avant tout INSERT si la dimension est fausse

    digest = hashlib.sha256(doc.content.encode("utf-8")).hexdigest()
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                INSERT INTO rag_document(source_uri,title,kind,language,version,content_sha256,is_active)
                VALUES (%s,%s,%s,%s,%s,%s,true)
                ON CONFLICT (source_uri, version) DO UPDATE
                SET title=EXCLUDED.title, content_sha256=EXCLUDED.content_sha256,
                    is_active=true
                RETURNING id
            """, (doc.sourceUri, doc.title, doc.kind, doc.language, doc.version, digest))
            document_id = cur.fetchone()[0]
            cur.execute("DELETE FROM rag_chunk WHERE document_id=%s", (document_id,))
            for index, (chunk, vector) in enumerate(zip(chunks, vectors)):
                metadata = {k: v for k, v in chunk.items()
                            if k not in ("content", "token_count")}
                cur.execute("""
                    INSERT INTO rag_chunk(document_id,ordinal,content,token_count,metadata,embedding)
                    VALUES (%s,%s,%s,%s,%s::jsonb,%s::vector)
                """, (document_id, index, chunk["content"], chunk["token_count"],
                      json.dumps(metadata, ensure_ascii=False), to_pgvector(vector)))
    return {"documentId": str(document_id), "chunksCreated": len(chunks),
            "embeddingModelKey": settings.embedding_model}


@app.post("/search")
def search(req: SearchRequest) -> dict:
    return {"results": hybrid_search(req.query, req.kind, req.k)}


@app.post("/context/build")
def context(req: ContextRequest) -> dict:
    return build_context(req.targetApp, req.hints, req.requirementTexts, req.maxTokens)
