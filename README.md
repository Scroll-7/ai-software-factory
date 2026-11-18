# AI Software Factory — projet personnel d'entraînement

Prototype local d'une « software factory » à base d'agents IA : une demande métier en français devient des spécifications, une architecture, du code, des tests, puis une **Draft PR GitHub** que **tu** relis et **tu** fusionnes manuellement.

> Ce dépôt est un exercice personnel préparatoire à un PFE. Il n'utilise **aucune donnée confidentielle**, **aucun modèle externe** et **aucune télémétrie**. Tout tourne en local avec des poids ouverts et des données synthétiques.

## Objectifs couverts

1. Transformer un besoin métier en spécifications fonctionnelles (agent **Specs**).
2. Générer l'architecture et le modèle de données (agent **Design**).
3. Générer les API et une partie du code (agent **Code**).
4. Générer les tests et les scénarios métier (agent **Tests**).
5. Vérifier et valider (agent **Review**) avant fusion humaine.

## Stack

| Rôle | Technologie |
|---|---|
| Orchestrateur, agents, REST, JWT, audit | Java 21 + Spring Boot + Spring AI |
| RAG, chunking, embeddings, contexte dépôt | Python + FastAPI + PostgreSQL/pgvector |
| Modèles locaux | Ollama, LLM 7B-Q4 (repli 3B-Q4), embedding français |
| Interface humaine | Angular + TypeScript |
| SCM / CI | GitHub, Draft PR, GitHub Actions, staging local docker compose |
| Cibles de démonstration | `Scroll-7/Fake-exposer` (i18n) et `demo-hr-app/` (CV fictifs FR) |

## Garantie « local-only »

- Aucun appel à Groq, Gemini, OpenAI, Anthropic ou à un service d'embedding cloud.
- Aucune clé API dans le code ; les variables d'environnement des services externes restent vides.
- Télémétrie désactivée : `ng analytics off`, usage ping GitHub désactivé si self-hosté, aucun exporteur Spring Boot vers l'extérieur.
- Le bac à sable d'exécution tourne avec `--network none`.

## Structure (en cours de construction)

```text
docs/              décisions, état d'avancement, rapport
infrastructure/   docker-compose, init.sql
factory-api/      orchestrateur Java 21
rag-service/      FastAPI + pgvector
check-runner/     image Docker de compilation/test/lint
web-ui/           Angular
demo-hr-app/      cible Angular + Spring Boot
evaluation/       tâches, métriques, résultats
```

Voir [`docs/BUILD_STATUS.md`](docs/BUILD_STATUS.md) pour l'état réel et [`docs/setup-windows.md`](docs/setup-windows.md) pour l'installation.

## Démarrage

Le guide d'installation est volontairement séparé : rien ne peut être lancé tant que JDK 21, Maven, Docker, Ollama et les poids ne sont pas présents sur la machine.
