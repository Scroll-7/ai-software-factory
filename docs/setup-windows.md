# Installation locale — Windows (à exécuter par toi)

**VERIFY :** tous les identifiants de paquets, tags d'images et noms de modèles ci-dessous changent. Vérifie-les au moment de l'exécution. Je n'ai rien installé et rien téléchargé.

## 1. Outils manquants détectés

JDK **21** (tu as 17), Maven, Docker, Ollama. Python 3.14 et Node/npm sont déjà présents.

## 2. JDK 21 + Maven

Exemple avec winget (**VERIFY** les identifiants) :

```powershell
winget install --id EclipseAdoptium.Jdk-21 -e
winget install --id Apache.Maven -e
java -version
mvn -version
```

Attendu : `java version "21..."`. Si `java -version` affiche encore 17, corrige `JAVA_HOME` et le `PATH`.

## 3. Docker + PostgreSQL/pgvector

```powershell
winget install --id Docker.DockerDesktop -e
docker --version
```

Puis (**VERIFY** le tag de l'image pgvector) :

```powershell
docker run -d --name pfe-pg `
  -e POSTGRES_USER=pfe -e POSTGRES_PASSWORD=pfe -e POSTGRES_DB=pfe_factory `
  -p 5432:5432 `
  pgvector/pgvector:pg16
```

Vérification :

```powershell
docker exec -it pfe-pg psql -U pfe -d pfe_factory -c "CREATE EXTENSION IF NOT EXISTS vector; SELECT extversion FROM pg_extension WHERE extname='vector';"
```

## 4. Ollama + poids locaux

```powershell
winget install --id Ollama.Ollama -e
ollama --version
```

À télécharger (**VERIFY** les tags exacts et les licences) :

```powershell
# 1 LLM 7B code en Q4_K_M (défaut, 8 Go VRAM)
# ollama pull <tag-7b-code-q4-verifié>

# 1 LLM 3B code en Q4 (repli)
# ollama pull <tag-3b-code-q4-verifié>

# 1 embedding français/multilingue — NOTER SA DIMENSION dans docs/models.md
# ollama pull <tag-embedding-fr-verifié>
```

Règle : **ne pas ingérer le corpus RAG avant de connaître la dimension** et d'avoir mis `vector(<dim>)` dans `init.sql`.

## 5. Télémétrie — à désactiver

```powershell
npx ng analytics off 2>$null   # si Angular CLI installé plus tard
```

- Ne jamais mettre de clé Groq/Gemini dans `Fake-exposer` pendant les démos de la factory.
- Ne pas activer d'exporteur Actuator vers l'extérieur.
- Désactiver l'usage ping si tu héberges un GitLab plus tard (non utilisé ici).

## 6. Compte GitHub

Créer un **fine-grained personal access token** avec accès en lecture/écriture aux dépôts visés, portée minimale, expiration courte. Le stocker dans une variable d'environnement locale, **jamais** dans un fichier commité :

```powershell
setx GH_PFE_TOKEN "<ton-token>"
```

Je n'ai pas besoin du token pour écrire le code ; il ne servira qu'au moment où **toi** décideras de pousser.

## 7. Ce que tu me renvoies

Après exécution, copie-colle la sortie de :

```powershell
java -version
mvn -version
docker --version
ollama --version
docker exec -it pfe-pg psql -U pfe -d pfe_factory -c "SELECT extversion FROM pg_extension WHERE extname='vector';"
```

Je corrigerai le scaffold en fonction des versions réelles.
