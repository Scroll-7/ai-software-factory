# Lot 2 — RAG service local

## Écrit

- `rag-service/app/config.py` — configuration par variables d'environnement `PFE_*`.
- `rag-service/app/db.py` — connexion psycopg **paresseuse** (`/health` fonctionne sans psycopg).
- `rag-service/app/embed.py` — embedding via Ollama localhost, avec repli `/api/embed` → `/api/embeddings`, contrôle strict de la dimension.
- `rag-service/app/chunk.py` — chunking Markdown par titre et code par symbole, avec en-tête `// FILE: ...`.
- `rag-service/app/retrieve.py` — recherche hybride pgvector + `tsvector('french')` + fusion RRF, max 2 chunks par document.
- `rag-service/app/context.py` — `/context/build` borné, déterministe, avec protection contre le path traversal.
- `rag-service/app/main.py` — endpoints `/health`, `/documents`, `/search`, `/context/build`.
- `rag-service/corpus/synthetic/**` — 6 documents synthétiques français (standards, ADR, modèles de tests).
- `rag-service/bin/ingest_corpus.py` — envoi du corpus au service local.
- `rag-service/tests/test_chunk.py` — tests unitaires sans base ni réseau.

## Dépendance manquante

`psycopg[binary]` n'est pas installé. **VERIFY** la compatibilité avec Python 3.14 :

```powershell
python -m pip install "psycopg[binary]"
```

Les autres dépendances sont déjà présentes (FastAPI 0.138, uvicorn 0.49, pydantic 2.12, numpy 2.4).

## Avant la première ingestion — ordre obligatoire

1. Installer Ollama et télécharger un embedding avec une dimension **connue**.
2. Mettre `PFE_EMBEDDING_MODEL` et `PFE_EMBEDDING_DIM` dans l'environnement.
3. Mettre la même dimension dans `factory-api/src/main/resources/schema.sql` (`vector(<dim>)`).
4. Recréer la base PostgreSQL si elle avait déjà été initialisée avec `vector(768)`.
5. Démarrer le service : `uvicorn app.main:app --reload --port 8000` depuis `rag-service/`.
6. Lancer `python bin/ingest_corpus.py`.

`embed.assert_dim` bloque l'ingestion si la dimension déclarée ne correspond pas : c'est volontaire, cela évite de corrompre l'index HNSW.

## Vérifications rapides

```powershell
cd rag-service
python -m unittest discover -s tests -v
curl http://localhost:8000/health
```

## Ce qui n'est pas encore fait

- Aucun appel réel à Ollama tant que le modèle n'est pas téléchargé.
- Aucune insertion en base tant que PostgreSQL/pgvector et psycopg ne sont pas présents.
- Le `RepoClient` Java pointe encore sur un bouchon : `/context/build` est prêt mais pas encore appelé par l'orchestrateur (Lot 3).
