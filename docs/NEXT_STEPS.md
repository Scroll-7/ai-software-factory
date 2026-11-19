# Prochaines étapes — à exécuter par toi, une à la fois

Rien n'est installé par l'assistant. Voici la suite, **dans l'ordre**. À chaque étape, copie-colle la commande, puis dis-moi ce qui s'est passé.

## Étape 4 — Installer Docker Desktop

1. Télécharge et installe **Docker Desktop for Windows** depuis le site officiel.
2. Redémarre l'ordinateur si demandé.
3. Ouvre Docker Desktop et attends qu'il affiche « Running ».
4. Dans PowerShell, vérifie :

```powershell
docker --version
```

Attendu : un numéro de version. Si « not recognized », redémarrePowerShell après l'installation.

## Étape 5 — Installer Ollama

1. Télécharge et installe **Ollama for Windows** depuis le site officiel.
2. Vérifie :

```powershell
ollama --version
```

## Étape 6 — Installer le driver PostgreSQL Python ✅ FAIT (2026-11-19)

`python -m pip install "psycopg[binary]"` → **psycopg 3.3.9** vérifié par import.

## Étape 7 — Démarrer PostgreSQL + pgvector ✅ FAIT (2026-11-18)

```powershell
docker run -d --name pfe-pg -e POSTGRES_USER=pfe -e POSTGRES_PASSWORD=pfe -e POSTGRES_DB=pfe_factory -p 5432:5432 pgvector/pgvector:pg16
docker exec -it pfe-pg psql -U pfe -d pfe_factory -c "CREATE EXTENSION IF NOT EXISTS vector; SELECT extversion FROM pg_extension WHERE extname='vector';"
```

Résultat réel : conteneur `pfe-pg` **Up**, `pg_isready` OK, `vector | 0.8.7`. Rien à refaire.

Schéma appliqué le 2026-11-19 via `Get-Content -Raw schema.sql | docker exec -i pfe-pg psql ... -v ON_ERROR_STOP=1 -f -`. Vérifié : `rag_chunk.embedding` = `vector(1024)`.

## Étape 8 — Télécharger les modèles locaux ✅ FAIT (2026-11-19)

`ollama list` → `qwen2.5-coder:7b` (4,7 Go) et `bge-m3:latest` (1,2 Go).
Vérification réelle : `POST http://localhost:11434/api/embed` avec `model: "bge-m3"`
→ **1024 dimensions**. Le schéma `vector(1024)` est donc correct.

```powershell
ollama pull bge-m3
ollama pull qwen2.5-coder:7b
```

Sources vérifiées : `ollama.com/library/bge-m3` (567M, 8K, 100+ langues) et
`ollama.com/library/qwen2.5-coder:7b` (Q4_K_M, 4,7 Go). Dimension BGE-M3 = **1024**
(fiche officielle BAAI/bge-m3) → `vector(1024)` déjà écrit dans `schema.sql`.

Repli si la machine manque de mémoire : `ollama pull qwen2.5-coder:3b` (Q4_K_M, 1,9 Go).

Après le téléchargement, vérifie :

```powershell
ollama list
```

Attendu : les lignes `bge-m3` et `qwen2.5-coder:7b`.

## Étape 9 — Premier lancement (en cours)

✅ RAG service démarré et corpus ingéré (6 documents / 20 chunks, recherche française OK).
✅ Lots 3–4 écrits : `RepoClient`, `RagClient`, `CheckClient` Docker `--network none`, `ScmClient` GitHub, `seed.sql`.
✅ API démarrée sur **http://127.0.0.1:8081** (pas 8080 : ce port est occupé par Oracle `TNSLSNR.EXE`, on ne l'arrête pas).

✅ **TC-01 est vert** (2026-11-19) : `SPECS → DESIGN → CODE → TESTS → REVIEW → AWAITING_REVIEW`. Branche poussée sur `Scroll-7/Fake-exposer`, aucune fusion.

Rejouer le test :

```powershell
powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1
```

Le script affiche `RUNNING` puis `AWAITING_REVIEW` avec l'URL de comparaison GitHub (ou `FAILED` avec l'erreur).

## Étape 10 — Créer la Draft PR à la main (à faire par toi, sans secret)

Comme `PFE_GITHUB_TOKEN` n'est pas défini, l'API renvoie une URL de comparaison. Ouvre-la dans GitHub, choisis **Create pull request**, puis **Draft pull request**. Ne fusionne pas.

Dernière URL obtenue :
`https://github.com/Scroll-7/Fake-exposer/compare/main...factory%2Fad0b83c6-2c49-4f8f-9375-15f756ba7573%2F4e428258-dbf0-45a3-b04b-b5dc09e3f3f4`

---

### Pour info — pourquoi 8081

Sur cette machine, `Get-NetTCPConnection -LocalPort 8080` montre `TNSLSNR.EXE` (Oracle).
L'API utilise donc `server.port: ${PFE_API_PORT:8081}`. Rien à faire de ton côté.

---

Règle : **une étape à la fois**. Tu ne devines rien, je te dis quoi taper.
