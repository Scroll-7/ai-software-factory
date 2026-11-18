# Lot 1 — ce qui est écrit, ce qui est factice

## Écrit et utilisable après installation

- `factory-api/pom.xml` — versions **vérifiées et figées** : Spring Boot 3.5.16, Spring AI 1.0.9, JJWT 0.12.6 (plus de placeholder).
- `factory-api/src/main/resources/application.yml` — modèles **vérifiés le 2026-11-18** : `qwen2.5-coder:7b` (Q4_K_M) et `bge-m3`. Le secret JWT reste une valeur de dev à remplacer par une clé locale aléatoire.
- `schema.sql` — schéma complet, idempotent, `tsvector('french')`, `vector(1024)` (dimension **vérifiée** de BGE-M3).
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

1. ✅ Fait — versions vérifiées (Maven Central) et figées dans `pom.xml`.
2. ✅ Fait — JDK 21 installé (`java -version` → 21.0.13).
3. ✅ Fait — dimension figée à 1024 (BGE-M3), avant toute insertion.
4. ✅ Fait — artefact `spring-ai-starter-model-ollama` vérifié ; noms des classes confirmés par la compilation.
5. ✅ Fait — API JJWT confirmée par la compilation.
6. ✅ Fait — `mvn -q -DskipTests compile` → BUILD SUCCESS.
7. Reste à faire par toi : `ollama pull qwen2.5-coder:7b` et `ollama pull bge-m3`.

## Choix d'architecture pris dans ce lot

- **Spring JDBC** plutôt que JPA/Hibernate : le schéma est la source de vérité, les requêtes sont visibles, l'audit reste explicite.
- **Validation maison** plutôt qu'un validateur JSON Schema complet : couvre exactement les mots-clés de nos schémas et évite une dépendance de plus à ce stade. À remplacer si tu veux un validateur normé.
- **Ports + bouchons** : l'orchestrateur est écrit une fois, les implémentations RAG/Docker/GitHub arrivent ensuite sans le modifier.
- **Aucun appel externe** dans le code : seule l'URL `http://localhost:11434` apparaît dans la configuration.
