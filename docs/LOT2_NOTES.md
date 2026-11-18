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

1. ✅ Ollama 0.40.16 installé. À télécharger : `ollama pull bge-m3` (1024 dims, vérifié).
2. ✅ Valeurs par défaut déjà posées dans `config.py` : `bge-m3` / `1024` (surchargeables par `PFE_EMBEDDING_MODEL` / `PFE_EMBEDDING_DIM`).
3. ✅ Même dimension dans `schema.sql` : `vector(1024)`.
4. ✅ Base encore vide : aucun `INSERT` n'a eu lieu, donc rien à recréer.
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
