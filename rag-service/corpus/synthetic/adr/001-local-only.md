# ADR 001 — Inférence entièrement locale

## Contexte
Prototype personnel de software factory. Aucune donnée confidentielle n'est autorisée, le budget est nul, et le matériel est un laptop RTX 3050 (8 Go VRAM), i5 13e génération, 16 Go de RAM.

## Décision
Utiliser un LLM ouvert quantifié servi localement par Ollama, et un modèle d'embedding local. Aucun appel à une API de modèle externe. Les poids publics sont téléchargés une fois pendant l'installation, puis l'exécution se fait hors ligne.

## Conséquences
- Le modèle est remplaçable par configuration (`model_config.key`), et chaque run enregistre le modèle utilisé.
- Avec 8 Go de VRAM, un 7B en Q4 avec contexte 4096 est la cible ; un 3B Q4 sert de repli.
- Le modèle d'embedding n'est chargé que pendant l'ingestion, puis déchargé ; le LLM est chargé pendant la génération. Les deux ne cohabitent pas.
- La qualité du code généré est inférieure à un assistant commercial ; la valeur du projet réside dans le RAG, la validation, les retries bornés, le bac à sable et la traçabilité.

## Alternatives écartées
- Assistant commercial : viole la contrainte local-only et le budget nul.
- Embedding cloud : viole la contrainte de confidentialité.
