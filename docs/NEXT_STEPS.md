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

## Étape 6 — Installer le driver PostgreSQL Python

```powershell
python -m pip install "psycopg[binary]"
```

Si ça échoue à cause de Python 3.14, dis-moi — on utilisera une version précise.

## Étape 7 — Démarrer PostgreSQL + pgvector

```powershell
docker run -d --name pfe-pg -e POSTGRES_USER=pfe -e POSTGRES_PASSWORD=pfe -e POSTGRES_DB=pfe_factory -p 5432:5432 pgvector/pgvector:pg16
docker exec -it pfe-pg psql -U pfe -d pfe_factory -c "CREATE EXTENSION IF NOT EXISTS vector; SELECT extversion FROM pg_extension WHERE extname='vector';"
```

Attendu : une ligne avec `extversion` (par exemple `0.8.0`).

## Étape 8 — Télécharger les modèles locaux

**NE PAS encore choisir au hasard.** Dis-moi quand tu es à cette étape : je te donnerai 2 ou 3 tags exacts à vérifier (un embedding français et un LLM 7B coder en Q4_K_M). Je te dirai aussi quelle dimension mettre dans `vector(<dim>)`.

## Étape 9 — Premier lancement

Quand tout est vert, je te donnerai les commandes pour démarrer le RAG service et l'API, puis on testera une requête complète.

---

Règle : **une étape à la fois**. Tu ne devines rien, je te dis quoi taper.
