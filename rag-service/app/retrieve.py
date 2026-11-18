"""Recherche hybride pgvector + tsvector('french') avec fusion RRF."""
from .config import settings
from .db import connection
from .embed import embed, to_pgvector


SEARCH_SQL = """
WITH semantic AS (
  SELECT c.id, 1 - (c.embedding <=> %s::vector) AS score
  FROM rag_chunk c JOIN rag_document d ON d.id = c.document_id
  WHERE d.is_active = true {kind1}
  ORDER BY c.embedding <=> %s::vector
  LIMIT %s
),
lexical AS (
  SELECT c.id, ts_rank_cd(c.content_tsv, plainto_tsquery('french', %s)) AS score
  FROM rag_chunk c JOIN rag_document d ON d.id = c.document_id
  WHERE c.content_tsv @@ plainto_tsquery('french', %s)
    AND d.is_active = true {kind2}
  ORDER BY score DESC
  LIMIT %s
),
fused AS (
  SELECT id, SUM(1.0 / (60 + rnk)) AS rrf_score
  FROM (
    SELECT id, ROW_NUMBER() OVER (ORDER BY score DESC) AS rnk FROM semantic
    UNION ALL
    SELECT id, ROW_NUMBER() OVER (ORDER BY score DESC) AS rnk FROM lexical
  ) ranked
  GROUP BY id
)
SELECT c.id, d.source_uri, d.title, d.kind, c.content, c.metadata, f.rrf_score
FROM fused f
JOIN rag_chunk c ON c.id = f.id
JOIN rag_document d ON d.id = c.document_id
ORDER BY f.rrf_score DESC
LIMIT %s
"""


def hybrid_search(query: str, kind: str | None = None, k: int = 5) -> list[dict]:
    vector = to_pgvector(embed([query])[0])
    kind1 = " AND d.kind = %s" if kind else ""
    kind2 = " AND d.kind = %s" if kind else ""
    params: list = [vector]
    if kind:
        params.append(kind)
    params += [vector, k * 3]
    params += [query, query]
    if kind:
        params.append(kind)
    params += [k * 3, k]

    sql = SEARCH_SQL.format(kind1=kind1, kind2=kind2)
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()

    seen_per_doc: dict[str, int] = {}
    out = []
    for row in rows:
        chunk_id, source_uri, title, doc_kind, content, metadata, score = row
        count = seen_per_doc.get(source_uri, 0)
        if count >= 2:  # diversité : au plus 2 chunks par document
            continue
        seen_per_doc[source_uri] = count + 1
        out.append({
            "chunkId": chunk_id,
            "sourceUri": source_uri,
            "title": title,
            "kind": doc_kind,
            "content": content,
            "metadata": metadata,
            "score": float(score),
        })
    return out
