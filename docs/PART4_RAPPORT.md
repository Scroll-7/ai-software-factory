# Partie 4 — Rapport de projet

**AI Software Factory — usine logicielle locale et hors ligne**
Version 1.0 — 2026-11-19. Les faits vérifiés sur la machine sont distingués des hypothèses (`ASSUMPTION`) et des points à confirmer (`VERIFY`).

---

## 1. Résumé exécutif

Ce projet construit une **usine logicielle personnelle** qui transforme une demande métier française en code testé, revu et proposé sous forme de branche GitHub en attente de décision humaine. Tout tourne sur la machine locale : modèle de langage Ollama, embeddings locaux, base PostgreSQL/pgvector, service RAG FastAPI, API Spring Boot et interface Angular.

À ce stade, le **scénario d'acceptation TC-01 est vert de bout en bout** : exigences → architecture → code → tests Docker isolés → revue → branche poussée sur `Scroll-7/Fake-exposer`, sans fusion automatique. Les Lots 0 à 6 et la Partie 3 (conception) sont livrés.

Le résultat n'est pas un produit fini : c'est un **prototype auditable**, avec des garde-fous déterministiques compensant les limites d'un modèle local 7B, et une évaluation préparée mais non encore exécutée.

---

## 2. Contexte et problématique

Le point de départ est un besoin simple : passer d'une idée métier rédigée en français à un changement de code vérifiable, sans dépendre d'un service d'IA externe, sans coût récurrent et sans exposer de données.

Contraintes fortes :

- **0 €** de budget : open-source uniquement, matériel déjà disponible.
- **Aucune API de modèle externe** : ni Groq, ni Gemini, ni OpenAI.
- **Aucune télémétrie**, aucun secret, aucune donnée réelle : tout est synthétique.
- **GitHub uniquement** : Draft PR, revue et fusion manuelles.
- Une seule personne : le système doit être rejouable par une commande.

**ASSUMPTION :** la valeur pédagogique du projet tient autant aux contrôles qu'à la génération. Un pipeline qui refuse de livrer du code non vérifié est plus utile qu'un générateur rapide mais silencieusement faux.

---

## 3. Objectifs et périmètre

| Objectif | État |
|---|---|
| Générer exigences, plan, code, tests et revue depuis un texte métier | ✅ réalisé |
| Ancrer les exigences dans un corpus RAG local | ✅ réalisé |
| Exécuter les tests générés dans un conteneur sans réseau | ✅ réalisé |
| Arrêter le pipeline sur erreur répétée (circuit breaker) | ✅ réalisé |
| Pousser une branche et proposer une Draft PR manuelle | ✅ réalisé et vérifié |
| Tracer chaque tentative et chaque décision dans une base auditable | ✅ réalisé |
| Offrir une interface web locale | ✅ réalisé et vérifié |
| Préparer une évaluation 12 tâches / 2 modèles | ✅ préparé, ❌ non exécuté |
| Activer le modèle 3B | ❌ en attente de l'utilisateur |

---

## 4. Solution retenue

Architecture en cinq briques : `factory-ui` (Angular), `factory-api` (Spring Boot), `rag-service` (FastAPI), PostgreSQL + pgvector, Ollama. Voir le document de conception (`docs/PART3_CONCEPTION.md`) pour les diagrammes et le modèle de données.

Le choix central est la **séparation entre génération et décision** :

- Le modèle propose du JSON, jamais du code directement exploitable.
- Des validateurs déterministes (schémas JSON + règles métier) acceptent ou refusent la sortie.
- Des normalisations corrigent les erreurs systématiques du 7B (sorties nues, imports ESM manquants, traçabilité incomplète, clés i18n déséquilibrées).
- Le code n'est exécuté que dans Docker `--network none`.
- La fusion est toujours humaine.

---

## 5. Réalisations par lot

| Lot | Contenu | Preuve |
|---|---|---|
| 0 | Squelette, README, `.gitignore`, guide Windows, état de construction | commits initiaux |
| 1 | API Java 21, 5 schémas/prompts v1, schéma SQL complet | `mvn compile` OK |
| 2 | RAG FastAPI, ingestion, chunking, recherche hybride | 6 documents / 20 chunks, `/health` OK |
| 3 | Boucle generate/check/retry, check Docker, normaliseur d'erreurs | TC-01 vert |
| 4 | `ScmClient` GitHub, webhook HMAC, workflow cible, protection documentée | branche vérifiée, 200/401 |
| 5 | UI Angular, `/auth/dev-token`, CORS | build vert, page 200 |
| 6 | Harnais 12 tâches, 2 modèles, baseline manuelle | `dry-run` vert |
| Partie 3 | Document de conception | `docs/PART3_CONCEPTION.md` |

---

## 6. Résultats et preuves

### 6.1 TC-01 — preuve de bout en bout

Run `4e428258-dbf0-45a3-b04b-b5dc09e3f3f4` (2026-11-19) :

```text
SPECS ✅ → DESIGN ✅ → CODE ✅ (4 fichiers) → TESTS ✅ (Docker) → REVIEW ✅ → AWAITING_REVIEW
```

- Branche `factory/ad0b83c6-2c49-4f8f-9375-15f756ba7573/4e428258-dbf0-45a3-b04b-b5dc09e3f3f4` poussée sur `Scroll-7/Fake-exposer`.
- Présence confirmée par `git ls-remote` (`4f5fe628…`).
- Aucune fusion, aucune PR créée automatiquement : une URL de comparaison a été renvoyée.
- Rejouable : `powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1`.

### 6.2 Contrôles techniques

- PostgreSQL 16.16, pgvector 0.8.7, embedding BGE-M3 **1024 dimensions** vérifié.
- Docker check : `node --test test/*.test.js` dans un conteneur `--network none`.
- Webhook GitHub : signature valide → `recorded: true`, signature fausse → **401**.
- Décision humaine : `APPROVE` renvoyé par l'UI est normalisé en `APPROVED` dans la base (testé).
- UI Angular 21.2.26 / TypeScript 5.9 : `npm run build` réussi (bundle initial 208,62 kB), page servie sur `:4200`, connexion locale testée dans le navigateur.
- Harnais : 12 tâches validées en `dry-run`, 0 appel réseau, rapport généré avec une ligne synthétique puis nettoyé.

**VERIFY (clos) :** TC-01 a été rejoué le 2026-11-19 sur le code actuel : run `098b26f5-58d3-4dce-a88c-6a37871a4aa1` vert, avec le template de workflow cible vérifié dans la branche poussée (`cef9eaf…`). Le premier essai avait révélé un bug de workspace résiduel, corrigé par `RepoClient.resetToDefault()`. Voir `docs/BUILD_STATUS.md`.

---

## 7. Analyse critique

### Forces

- **Traçabilité** : chaque run, chaque tentative, chaque fichier et chaque décision est persisté ; le journal d'audit est chaîné par hachage.
- **Refus explicite** : le pipeline s'arrête avec `AWAITING_HUMAN` plutôt que de livrer du code dont la signature d'erreur se répète.
- **Exécution isolée** : les tests générés tournent sans réseau.
- **Déterminisme** : les garde-fous ne dépendent pas de la bonne volonté du modèle.
- **Coût maîtrisé** : aucun service payant, aucun secret externe.

### Faiblesses

- Le modèle 7B reste fragile : il a fallu 9 corrections successives pendant TC-01 (parité i18n, ESM, imports manquants, traçabilité).
- Les compteurs de jetons ne sont pas remontés par l'API : les métriques de coût/performance sont partielles.
- La qualité métier n'est pas notée automatiquement : seule la réussite technique est mesurée.
- Le webhook ne peut pas recevoir d'événements GitHub réels en 100 % local.
- Le modèle 3B n'est pas installé ; la comparaison 7B / 3B n'a donc pas encore de résultat.

### Risques

| Risque | Mitigation |
|---|---|
| Le modèle invente une API ou un import | `DomainValidator` + revue + tests Docker |
| Fuite de données réelles | Corpus synthétique, consignes prompts, contrôle des questions bloquantes |
| Fusion accidentelle | Aucune fusion automatique, protection de branche documentée, décision humaine |
| Dérive de version (Spring AI, Angular, pgvector) | `VERIFY` explicites dans les docs |

---

## 8. Évaluation

Le harnais du Lot 6 prépare **12 tâches fictives** : 6 sur Fake-exposer (i18n, tests, sécurité, documentation) et 6 sur demo-hr-app (score CV, biais, explication, audit). Deux modèles sont prévus : `ollama-local` (7B, défaut) et `ollama-local-3b` (déclaré mais inactif).

La baseline manuelle est un fichier à remplir par l'humain (minutes + note de qualité). Le rapport compare durée usine et durée manuelle par tâche et par modèle.

**ASSUMPTION :** une vingtaine de runs suffisent pour observer une tendance qualitative ; ce n'est pas une étude statistique.

**VERIFY :** aucun run réel n'a été lancé : chacun pousserait une branche GitHub. L'activation du 3B nécessite `ollama pull qwen2.5-coder:3b`, étape machine à faire par l'utilisateur.

---

## 9. Sécurité, éthique, confidentialité

- Aucun appel réseau sortant dans le code généré : motifs interdits (`groq`, `gemini`, `openai`, `fetch(`, `axios`, `HttpClient`, `child_process`, `subprocess`, `API_KEY`, `require(`) et revue avec `externalCalls` vide.
- Aucun attribut protégé (âge, sexe, origine) dans le score CV : checklist de biais obligatoire pour `demo-hr-app` ; le score reste une aide à la décision.
- Données exclusivement fictives et françaises.
- Jeton JWT local de développement : secret par défaut documenté comme « dev only », jamais un secret réel.
- Webhook : HMAC-SHA256 vérifié avant toute écriture en audit.
- Aucune télémétrie (`factory.telemetry.enabled=false`).

**ASSUMPTION :** le secret de développement par défaut doit être remplacé avant toute exposition du service, même sur le réseau local.

---

## 10. Coût et empreinte

| Poste | Coût |
|---|---|
| Licences | 0 € (open-source) |
| Inférence | 0 € (Ollama local, électricité uniquement) |
| Hébergement | 0 € (machine existante) |
| GitHub | 0 € (offre gratuite, dépôts publics) |
| Stockage | Modèles 4,7 Go + 1,2 Go, base locale, artefacts de run |

Le surcoût réel est le temps de développement et la consommation électrique, non mesurés ici.

---

## 11. Leçons apprises

1. Un 7B local **ne respecte pas spontanément un schéma JSON** : il faut parser défensivement et normaliser.
2. Une règle métier déterministe vaut mieux qu'une consigne répétée dans le prompt (parité i18n, imports ESM).
3. Une erreur isolée se répare ; une **signature répétée** doit arrêter le pipeline.
4. Les sorties « presque correctes » sont le vrai danger : le test Docker est le dernier filet fiable.
5. La traçabilité n'est pas un luxe : elle a permis de diagnostiquer chaque échec de TC-01 en une requête SQL.
6. La contrainte « 100 % local » simplifie la sécurité mais complique l'intégration GitHub (webhook non joignable depuis l'extérieur).

---

## 12. Conclusion et perspectives

Le prototype atteint son objectif principal : **une demande française peut produire un changement de code vérifié et proposé à la revue humaine, sans quitter la machine**. TC-01 en est la preuve reproductible.

Les prochaines étapes sont : rejouer TC-01 après les derniers ajouts, installer et activer le 3B, exécuter les runs d'évaluation, remplir la baseline manuelle, puis produire la soutenance fictive (Partie 5) et la checklist de clôture (Partie 6).

---

## 13. Livrables associés

- `docs/PART3_CONCEPTION.md` — conception détaillée.
- `docs/BUILD_STATUS.md` — état des lots et décisions verrouillées.
- `docs/GITHUB_SETUP.md` — réglages GitHub manuels.
- `docs/eval/README.md` — procédure d'évaluation.
- `docs/NEXT_STEPS.md` — étapes machine à exécuter par l'utilisateur.
- `scripts/tc01.ps1` — scénario d'acceptation en une commande.
