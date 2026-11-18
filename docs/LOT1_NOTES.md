# Lot 1 — ce qui est écrit, ce qui est factice

## Écrit et utilisable après installation

- `factory-api/pom.xml` — **contient 3 placeholders à remplacer** : `VERIFY_SPRING_BOOT_VERSION`, `VERIFY_SPRING_AI_VERSION`, `VERIFY_JJWT_VERSION`.
- `factory-api/src/main/resources/application.yml` — **contient des placeholders** : `VERIFY_7B_MODEL_TAG`, `VERIFY_EMBEDDING_MODEL_TAG`, `VERIFY_LOCAL_JWT_SECRET_AT_LEAST_256_BITS`. Vérifie aussi les noms de propriétés Spring AI.
- `schema.sql` — schéma complet, idempotent, `tsvector('french')`, `vector(768)` **à ajuster selon la dimension réelle de ton embedding**.
- `schemas/*.v1.json` — 5 schémas de sortie (Specs, Design, Code, Tests, Review).
- `prompts/v1/*.fr.txt` — prompt commun + 5 prompts spécifiques, français, versionnés.
- Java : orchestrateur, validateur JSON minimal, validateur métier, normaliseur d'erreurs, audit à chaîne de hachage, repository JDBC, REST minimal, JWT (API JJWT à vérifier).

## Volontairement factice (bouchons)

| Port | État | Lot |
|---|---|---|
| `RagClient` | renvoie un chunk `synthetic://stub` | 2 |
| `RepoClient` | lève `UnsupportedOperationException` | 3 |
| `CheckClient` | lève `UnsupportedOperationException` | 3 |
| `ScmClient` | lève `UnsupportedOperationException` | 4 |

Conséquence : **l'orchestrateur compile (si versions correctes) mais ne peut pas encore exécuter un run complet.** C'est normal et voulu.

## À vérifier avant le premier build

1. Remplacer les placeholders de version dans `pom.xml` et `application.yml`.
2. Installer JDK 21 (`java -version` doit afficher 21).
3. Remplacer `vector(768)` par la vraie dimension de l'embedding choisi.
4. Vérifier les noms exacts des classes Spring AI (`ChatModel`, `Prompt`) et de l'artefact Ollama.
5. Vérifier l'API JJWT (`Jwts.parser().verifyWith(...)` est supposée ici).
6. Lancer `mvn -q -DskipTests compile` et me renvoyer la sortie.

## Choix d'architecture pris dans ce lot

- **Spring JDBC** plutôt que JPA/Hibernate : le schéma est la source de vérité, les requêtes sont visibles, l'audit reste explicite.
- **Validation maison** plutôt qu'un validateur JSON Schema complet : couvre exactement les mots-clés de nos schémas et évite une dépendance de plus à ce stade. À remplacer si tu veux un validateur normé.
- **Ports + bouchons** : l'orchestrateur est écrit une fois, les implémentations RAG/Docker/GitHub arrivent ensuite sans le modifier.
- **Aucun appel externe** dans le code : seule l'URL `http://localhost:11434` apparaît dans la configuration.
