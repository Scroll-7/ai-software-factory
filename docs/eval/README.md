# Lot 6 — harnais d'évaluation local

Objectif : mesurer l'usine sur **12 tâches synthétiques**, avec **deux modèles locaux**, sans aucun service externe.

## Contenu

- `tasks.json` : 12 tâches fictives (6 `fake-exposer`, 6 `demo-hr-app`), rédigées en français.
- `baseline_manual.json` : temps et note de qualité **à remplir par toi**. Laisse `null` tant que ce n'est pas mesuré.
- `scripts/eval_harness.py` : orchestration locale (Python standard, aucune dépip à installer).
- `results/*.jsonl` : un run = une ligne (généré).
- `RAPPORT.md` : rapport Markdown français généré (généré).

## Étape 1 — vérifier sans effet de bord

```powershell
python scripts/eval_harness.py list
python scripts/eval_harness.py dry-run
```

Aucun appel réseau, aucune branche poussée.

## Étape 2 — activer le modèle 3B (à faire par toi, une seule fois)

Le 3B est **déclaré mais inactif** parce qu'il n'est pas encore présent sur cette machine. Vérifie :

```powershell
ollama list
```

Si `qwen2.5-coder:3b` est absent :

```powershell
ollama pull qwen2.5-coder:3b
```

Puis demande-moi d'activer la clé `ollama-local-3b` dans `seed.sql` (je ne modifie pas tes poids ni ton Ollama sans ton accord). Tant que la clé est inactive, l'API répond **400** si le harnais la demande — c'est voulu.

## Étape 3 — lancer UN run (attention : cela pousse une branche GitHub)

```powershell
python scripts/eval_harness.py run --task-id E01 --model ollama-local --i-understand-this-pushes-branches
```

Le harnais :
1. obtient un jeton local via `/api/v1/auth/dev-token` ;
2. crée la demande avec le `modelKey` choisi ;
3. lance `/runs/{id}/execute` et sonde `/execution` toutes les 5 s ;
4. enregistre statut, durée, événements et nombre de réparations CODE dans un `.jsonl`.

**Succès technique** = `AWAITING_REVIEW` (branche poussée, en attente de toi). Aucune fusion, aucune PR automatique.

Pour lancer toute la matrice (12 tâches × 2 modèles = 24 runs, donc 24 branches), dis-moi explicitement que tu veux que je prépare une boucle. Je ne la lance pas seul.

## Étape 4 — baseline manuelle et rapport

Remplis `baseline_manual.json` (minutes réellement passées, note de qualité de 1 à 5). Puis :

```powershell
python scripts/eval_harness.py report
```

Le rapport compare les durées usine et manuelles, par modèle et par tâche.

## Limites connues

- Une seule machine, un seul Ollama local : les durées ne sont pas comparables à un cloud.
- Les compteurs de jetons ne sont pas encore exposés par l'API (ils restent à 0 dans `agent_output`).
- La qualité n'est pas notée automatiquement : la revue humaine reste la référence.
- Chaque run crée une branche sur le dépôt cible. Nettoyage des branches : **à faire par toi dans GitHub**.
