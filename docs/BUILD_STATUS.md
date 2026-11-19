# État de construction — AI Software Factory (projet personnel d'entraînement)

Démarré le **2026-11-18**. Objectif : W1 → W26 (≈ 2027-05-19). Une personne.

Mode de travail retenu : **je produis le code et les documents, tu valides par prompts, tu exécutes les étapes qui nécessitent ta machine ou ton compte GitHub.**

## Décisions verrouillées (Parts 1 et 2)

- Dépôt SCM : **GitHub** (`Scroll-7/...`), PR en Draft, fusion manuelle par toi, staging local.
- Cible i18n : **`Scroll-7/Fake-exposer`** (vanilla JS + Express + FastAPI Python), sans appel Groq/Gemini.
- Cible CV : **`demo-hr-app/`** (Angular + TypeScript + Spring Boot), CV fictifs **français uniquement**.
- Plan de contrôle : Java 21 + Spring Boot + Spring AI, Angular UI, FastAPI RAG, PostgreSQL + pgvector, Ollama local.
- RAG : corpus **synthétique** français, hibryde cosine + `tsvector('french')`.
- Modèles (vérifiés 2026-11-18) : `qwen2.5-coder:7b` Q4_K_M par défaut, repli `qwen2.5-coder:3b` Q4_K_M ; embedding `bge-m3` (1024 dims, multilingue FR) chargé uniquement à l'ingestion.
- Prompts : français, versionnés `v1`, identifiants de code en anglais.
- Aucun service externe, aucune télémétrie, aucune donnée confidentielle.

## Environnement détecté sur cette machine (2026-11-18)

| Outil | État | Action requise |
|---|---|---|
| `git` | ✅ présent | — |
| `python` 3.14 | ✅ présent (FastAPI 0.138, uvicorn 0.49, pydantic 2.12, numpy 2.4, psycopg **3.3.9**) | — |
| `node` 24 / `npm` 11 | ✅ présents | — |
| `java` 21.0.13 | ✅ installé (JDK 17 conservé aussi) | — |
| `maven` 3.10.1 | ✅ installé (via Chocolatey) | — |
| `docker` 29.9.3 | ✅ installé (Virtual Machine Platform + WSL activés, redémarrage fait) | — |
| `ollama` 0.40.16 | ✅ installé (`ollama --version` confirmé) | — |
| poids des modèles | ✅ `qwen2.5-coder:7b` (4,7 Go) et `bge-m3:latest` (1,2 Go) téléchargés | — |
| PostgreSQL + pgvector | ✅ conteneur `pfe-pg` (pgvector **0.8.7**) sur `localhost:5432`, base `pfe_factory`, user `pfe` | — |
| Token GitHub | ❌ non fourni (push fait via Credential Manager) | **TOI** : PAT fin-grained uniquement si besoin API, jamais commité |

## Vérifications réelles passées (2026-11-18)

- `mvn -q -DskipTests compile` → **BUILD SUCCESS** (Spring Boot 3.5.16 + Spring AI 1.0.9 + JJWT 0.12.6).
- `python -m unittest discover -s tests -v` (rag-service) → **3 tests OK**.
- Versions vérifiées sur Maven Central, pas inventées. `spring-ai-starter-model-ollama` est le nom correct (l'ancien `-ollama-spring-boot-starter` n'existe plus en 1.0.x).
- Dépôt GitHub `Scroll-7/ai-software-factory` créé et pushé sur `master` (2 commits, arbre propre).
- `docker run -d --name pfe-pg ... pgvector/pgvector:pg16` → conteneur **Up**, `pg_isready` OK, `CREATE EXTENSION vector` → **0.8.7**. `pgcrypto` sera créé par `schema.sql` au 1er démarrage de l'API.
- Tags et dimension vérifiés sur ollama.com/library et la fiche officielle BAAI/bge-m3 : `qwen2.5-coder:7b` = Q4_K_M 4,7 Go ; `bge-m3` = 1024 dims. `vector(1024)` écrit dans `schema.sql` **avant** toute insertion.
- Vérification réelle locale (2026-11-19) : `POST /api/embed` avec `model: "bge-m3"` → `model=bge-m3`, `count=1024`. L'alias court `bge-m3` résout bien `bge-m3:latest`. Dimension confirmée, pas de re-embedding nécessaire.
- **Premier RAG réel (2026-11-19)** : schéma appliqué via `psql -f schema.sql` → `vector(1024)`. Service `uvicorn app.main:app --port 8000` démarré, `/health` vert. `python bin/ingest_corpus.py` → **6 documents / 20 chunks**, tous `vector_dims=1024`. `/search` avec une requête française renvoie `synthetic://standards/i18n.md` en tête. `/context/build` répond avec les clés `targetApp, tree, files, testCommands, tokenCount, note`.

## Lots de construction

- [x] **Lot 0** — README, `.gitignore`, ce fichier, guide d'installation Windows.
- [x] **Lot 1** — squelette `factory-api` (Java 21, Spring Boot, Spring AI), schémas JSON des 5 agents, prompts `v1`, SQL complet. Voir `docs/LOT1_NOTES.md`.
- [x] **Lot 2** — `rag-service` FastAPI (ingestion, chunking, embedding, retrieval, `/context/build`). Voir `docs/LOT2_NOTES.md`.
- [x] **Lot 3** — boucle generate/check/retry réelle + `check-runner` Docker + normaliseur d'erreurs. **TC-01 vert le 2026-11-19.**
- [x] **Lot 4** — intégration GitHub (`ScmClient`, Draft PR, webhook, `.github/workflows/`). **Branche + push vérifiés, webhook HMAC vérifié, workflow CI cible généré de façon déterministe, protection de branche documentée. Draft PR manuelle (pas de `PFE_GITHUB_TOKEN`).** Voir `docs/GITHUB_SETUP.md`.
- [x] **Lot 5** — UI Angular (requête, suivi du pipeline, URL de Draft PR, validation humaine). `npm run build` vert le 2026-11-19. Voir `docs/LOT5_NOTES.md`.
- [ ] **Lot 6** — harnais d'évaluation (10–15 tâches, deux modèles, baseline manuelle).
- [ ] **Lot 7** — docs françaises, rapport, mock defense (Parts 3–6 à livrer quand tu dis `continue`).

## Changements du 2026-11-19 (Lots 3–4, en validation)

- L'API écoute sur **8081**, pas 8080 : le port 8080 est occupé par l'écouteur Oracle `TNSLSNR.EXE`. On ne touche pas à Oracle. Surcharge possible : `PFE_API_PORT`.
- `seed.sql` ajoute 3 utilisateurs locaux fictifs et les clés `ollama-local` / `ollama-embed-local` (FK obligatoires).
- `RagClient` réel (`/search` sur le service local), `RepoClient` réel (clone superficiel dans `workspaces/`), `CheckClient` Docker `--network none`, `ScmClient` GitHub (branche + commit + push, Draft PR si token).
- Bugs corrigés en cours de route : séparateur SQL `;;` pour Spring, `created_at` en `Timestamp`, casts des colonnes `enum` PostgreSQL, casse des noms de stages dans les validateurs, placeholder `{{TASK}}` non renseigné, consigne « ne pas recopier le schéma » ajoutée au prompt.
- `/error` rendu public pour ne plus masquer les 500 en 403.
- `POST /runs/{id}/execute` est devenu **asynchrone** : il répond `202 RUNNING` tout de suite et `GET /runs/{id}/execution` indique `RUNNING`, `AWAITING_REVIEW` (avec la PR) ou `FAILED` (avec l'erreur). Cela évite les timeouts HTTP pendant que le modèle 7B génère plusieurs fichiers.
- Normalisations déterministes ajoutées : seuls les `source_uri` autorisés sont conservés en SPECS, et toute exigence non couverte est ajoutée au premier fichier du plan DESIGN. ASSUMPTION : nécessaire avec un modèle 7B local.

## Lot 4 — complété le 2026-11-19

- `POST /api/v1/webhooks/github` : vérification `X-Hub-Signature-256` (HMAC-SHA256), événement tracé dans `audit_log`, **aucun appel sortant**. Test réel : signature valide → `recorded: true`, signature fausse → **401**.
- Le workflow CI cible est un **template déterministe** (`templates/github/fake-exposer-ci.yml`), injecté sous `.github/workflows/factory-generated-ci.yml` sans écraser le `ci.yml` existant de Fake-exposer. Présent vérifié dans le jar.
- Correction : libellé d'événement `GITHUB_PR` ajouté à l'enum `stage_name` (migration idempotente), et décisions UI `APPROVE`/`REJECT` normalisées en `APPROVED`/`REJECTED` pour la table SQL. Test réel : `POST /approve` avec `APPROVE` → `APPROVED`.
- La CI du dépôt usine vérifie aussi le build Angular (`npm ci && npm run build`).

## Lot 5 — vérifié le 2026-11-19

- Angular **21.2.26** + TypeScript **5.9.x** (Angular 22 exige Node ≥ 24.15 ; la machine a Node 24.13).
- `npm run build` → **BUILD SUCCESS** (bundle initial 208,62 kB). `package-lock.json` commité.
- Serveur de dev sur `http://127.0.0.1:4200` → HTTP 200, rendu sans erreur console.
- CORS vérifié (`Access-Control-Allow-Origin: http://127.0.0.1:4200`) et bouton « Se connecter » vérifié dans le navigateur (`POST /api/v1/auth/dev-token` → 200).

## TC-01 — vert le 2026-11-19 (preuve de bout en bout)

Run `4e428258-dbf0-45a3-b04b-b5dc09e3f3f4` : `SPECS ✅ → DESIGN ✅ → CODE ✅ (4 fichiers) → TESTS ✅ (Docker `--network none`) → REVIEW ✅ → AWAITING_REVIEW`.

- Branche poussée et vérifiée : `factory/ad0b83c6-2c49-4f8f-9375-15f756ba7573/4e428258-dbf0-45a3-b04b-b5dc09e3f3f4` sur `Scroll-7/Fake-exposer` (`git ls-remote` → `4f5fe628…`).
- Aucune fusion, aucun merge automatique. L'URL renvoyée est une URL de **comparaison GitHub** à ouvrir manuellement pour créer la Draft PR (absence de `PFE_GITHUB_TOKEN`).
- Normalisations déterministes supplémentaires : garde-fou anti-boucle du modèle 7B (`repeat-penalty`, `temperature=0.1`), enveloppement des sorties CODE/TESTS nues, ajout des imports ESM manquants, parité des clés `fr.json`/`en.json`, traçabilité REVIEW complétée. ASSUMPTION : nécessaires avec un modèle local 7B.
- Rejouable en une commande : `powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1`.

## Règles pour moi

1. Je ne pousse rien sur GitHub sans ton accord explicite.
2. Je n'installe aucun logiciel, je ne télécharge aucun poids, je ne dépense rien.
3. Je modifie uniquement ce dépôt `C:\factory ai`, jamais `Fake-exposer` sans accord.
4. Je note `ASSUMPTION` / `VERIFY` sur tout ce qui n'est pas confirmé.
5. Chaque lot se termine par une liste de ce que **toi** dois exécuter et des logs à me renvoyer.
