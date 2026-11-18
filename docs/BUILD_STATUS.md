# État de construction — AI Software Factory (projet personnel d'entraînement)

Démarré le **2026-11-18**. Objectif : W1 → W26 (≈ 2027-05-19). Une personne.

Mode de travail retenu : **je produis le code et les documents, tu valides par prompts, tu exécutes les étapes qui nécessitent ta machine ou ton compte GitHub.**

## Décisions verrouillées (Parts 1 et 2)

- Dépôt SCM : **GitHub** (`Scroll-7/...`), PR en Draft, fusion manuelle par toi, staging local.
- Cible i18n : **`Scroll-7/Fake-exposer`** (vanilla JS + Express + FastAPI Python), sans appel Groq/Gemini.
- Cible CV : **`demo-hr-app/`** (Angular + TypeScript + Spring Boot), CV fictifs **français uniquement**.
- Plan de contrôle : Java 21 + Spring Boot + Spring AI, Angular UI, FastAPI RAG, PostgreSQL + pgvector, Ollama local.
- RAG : corpus **synthétique** français, hibryde cosine + `tsvector('french')`.
- Modèles : 7B-Q4 par défaut (8 Go VRAM), repli 3B-Q4 ; embedding français chargé uniquement à l'ingestion.
- Prompts : français, versionnés `v1`, identifiants de code en anglais.
- Aucun service externe, aucune télémétrie, aucune donnée confidentielle.

## Environnement détecté sur cette machine (2026-11-18)

| Outil | État | Action requise |
|---|---|---|
| `git` | ✅ présent | — |
| `python` 3.14 | ✅ présent (FastAPI 0.138, uvicorn 0.49, pydantic 2.12, numpy 2.4, torch CPU, transformers 5.12) | installer `psycopg[binary]` plus tard |
| `node` 24 / `npm` 11 | ✅ présents | — |
| `java` 21.0.13 | ✅ installé (JDK 17 conservé aussi) | — |
| `maven` 3.10.1 | ✅ installé (via Chocolatey) | — |
| `docker` | ❌ absent | **TOI** : installer Docker Desktop |
| `ollama` | ❌ absent | **TOI** : installer Ollama |
| poids des modèles | ❌ non téléchargés | **TOI** : `ollama pull <tag vérifié>` |
| PostgreSQL + pgvector | ❌ non installé | **TOI** : conteneur Docker pgvector |
| Token GitHub | ❌ non fourni | **TOI** : PAT fin-grained, jamais commité |

## Vérifications réelles passées (2026-11-18)

- `mvn -q -DskipTests compile` → **BUILD SUCCESS** (Spring Boot 3.5.16 + Spring AI 1.0.9 + JJWT 0.12.6).
- `python -m unittest discover -s tests -v` (rag-service) → **3 tests OK**.
- Versions vérifiées sur Maven Central, pas inventées. `spring-ai-starter-model-ollama` est le nom correct (l'ancien `-ollama-spring-boot-starter` n'existe plus en 1.0.x).

## Lots de construction

- [x] **Lot 0** — README, `.gitignore`, ce fichier, guide d'installation Windows.
- [x] **Lot 1** — squelette `factory-api` (Java 21, Spring Boot, Spring AI), schémas JSON des 5 agents, prompts `v1`, SQL complet. Voir `docs/LOT1_NOTES.md`.
- [x] **Lot 2** — `rag-service` FastAPI (ingestion, chunking, embedding, retrieval, `/context/build`). Voir `docs/LOT2_NOTES.md`.
- [ ] **Lot 3** — boucle generate/check/retry réelle + `check-runner` Docker + normaliseur d'erreurs.
- [ ] **Lot 4** — intégration GitHub (`ScmClient`, Draft PR, webhook, `.github/workflows/`).
- [ ] **Lot 5** — UI Angular (requête, specs, diff, revue, validation, suivi).
- [ ] **Lot 6** — harnais d'évaluation (10–15 tâches, deux modèles, baseline manuelle).
- [ ] **Lot 7** — docs françaises, rapport, mock defense (Parts 3–6 à livrer quand tu dis `continue`).

## Règles pour moi

1. Je ne pousse rien sur GitHub sans ton accord explicite.
2. Je n'installe aucun logiciel, je ne télécharge aucun poids, je ne dépense rien.
3. Je modifie uniquement ce dépôt `C:\factory ai`, jamais `Fake-exposer` sans accord.
4. Je note `ASSUMPTION` / `VERIFY` sur tout ce qui n'est pas confirmé.
5. Chaque lot se termine par une liste de ce que **toi** dois exécuter et des logs à me renvoyer.
