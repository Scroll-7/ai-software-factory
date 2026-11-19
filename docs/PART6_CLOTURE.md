# Partie 6 — Checklist de clôture

**AI Software Factory — usine logicielle locale et hors ligne**
Version 1.0 — 2026-11-19. Cette checklist clôt la rédaction en six parties. Elle distingue ce qui est **livré et vérifié**, ce qui est **préparé mais non exécuté**, et ce qui reste **à faire par l'utilisateur**. `ASSUMPTION` = hypothèse assumée, `VERIFY` = point à confirmer.

---

## 1. Livrables rédigés

| Partie | Fichier | Statut |
|---|---|---|
| Partie 1 — cahier des charges / MoSCoW | (livrée en chat) | ✅ |
| Partie 2 — architecture, agents, RAG, retry | (livrée en chat) | ✅ |
| Partie 3 — conception | `docs/PART3_CONCEPTION.md` | ✅ poussé |
| Partie 4 — rapport de projet | `docs/PART4_RAPPORT.md` | ✅ poussé |
| Partie 5 — soutenance fictive | `docs/PART5_SOUTENANCE.md` | ✅ poussé |
| Partie 6 — clôture | `docs/PART6_CLOTURE.md` | ✅ ce document |

Docs d'accompagnement : `docs/BUILD_STATUS.md`, `docs/NEXT_STEPS.md`, `docs/GITHUB_SETUP.md`, `docs/eval/README.md`.

---

## 2. Lots livrés

- [x] **Lot 0** — squelette, README, `.gitignore`, suivi de construction.
- [x] **Lot 1** — API Java 21, 5 schémas et prompts v1, schéma SQL complet.
- [x] **Lot 2** — service RAG FastAPI, corpus synthétique, recherche hybride.
- [x] **Lot 3** — boucle generate/check/retry, checks Docker `--network none`, normaliseur.
- [x] **Lot 4** — `ScmClient` GitHub, webhook HMAC, workflow cible, protection documentée.
- [x] **Lot 5** — interface Angular 21, jeton local, CORS.
- [x] **Lot 6** — harnais d'évaluation 12 tâches / 2 modèles, baseline manuelle.
- [x] **Lot 7** — documentation française, rapport, soutenance, clôture.

---

## 3. Preuves vérifiées sur la machine

| Preuve | Résultat |
|---|---|
| TC-01 de bout en bout | `SPECS → DESIGN → CODE → TESTS → REVIEW → AWAITING_REVIEW` (run `4e428258…`) |
| Branche distante | `git ls-remote` confirme le commit `4f5fe628…` sur `Scroll-7/Fake-exposer` |
| Webhook HMAC | signature valide → `recorded: true` ; fausse → **401** |
| Audit | événements `GITHUB_WEBHOOK`, `HUMAN_APPROVAL` ; aucune ligne `MERGE`/`DEPLOY` |
| Décision UI | `APPROVE` normalisé en `APPROVED` en base (testé) |
| Base de données | PostgreSQL 16.16, pgvector 0.8.7, `vector(1024)` vérifié |
| RAG | 6 documents / 20 chunks, `/search` et `/context/build` répondent |
| UI | `npm run build` vert, page servie sur `:4200`, connexion locale OK dans le navigateur |
| Harnais | `list` et `dry-run` verts (12 tâches, 0 appel réseau) ; rapport généré puis nettoyé |
| CI usine | ajout du build Angular dans `.github/workflows/ci.yml` |

`master` est synchronisé avec `origin/master` à chaque livraison.

---

## 4. Points `VERIFY` encore ouverts

| # | Point | Action requise |
|---|---|---|
| V1 | TC-01 n'a pas été rejoué depuis l'ajout du template de workflow cible et du paramètre `modelKey`. | Relancer `powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1`. |
| V2 | Le modèle `qwen2.5-coder:3b` n'est pas installé ; la clé `ollama-local-3b` est déclarée **inactive**. | Toi : `ollama pull qwen2.5-coder:3b`, puis demander l'activation de la clé. |
| V3 | Aucun run d'évaluation réel n'a été lancé (24 runs = 24 branches). | Toi : lancer les runs un par un selon `docs/eval/README.md`. |
| V4 | Le webhook public ne peut pas joindre `127.0.0.1` depuis GitHub. | Optionnel : tunnel/URL publique, hors cadre 100 % local. |
| V5 | Versions exactes relevées le 2026-11-19 (Spring Boot 3.5.16, Spring AI 1.0.9, Angular 21.2.26, Node 24.13.0…). | Confirmer après toute mise à jour. |
| V6 | Compteurs de jetons non remontés par l'API (`agent_output` à 0). | Évolution possible : exposer les métriques d'inférence. |

**ASSUMPTION :** ces points n'empêchent pas la clôture de la rédaction : ils concernent l'exploitation et l'évaluation, pas la conformité du prototype livré.

---

## 5. Actions à faire par toi (jamais par l'assistant)

1. **Protection de branche** — dans `Scroll-7/Fake-exposer` : 1 approbation, checks requis, fusion manuelle. Procédure : `docs/GITHUB_SETUP.md`.
2. **Draft PR manuelle** — ouvrir l'URL de comparaison renvoyée par TC-01, choisir « Draft pull request », ne pas fusionner.
3. **Webhook (optionnel)** — le tester en local avec la commande HMAC de `docs/GITHUB_SETUP.md` ; le brancher à GitHub demanderait une URL publique.
4. **Modèle 3B** — `ollama pull qwen2.5-coder:3b`, puis activation de la clé sur demande.
5. **Évaluation** — remplir `docs/eval/baseline_manual.json`, lancer les runs, générer `docs/eval/RAPPORT.md`.
6. **Nettoyage GitHub** — supprimer les branches de run devenues inutiles, à la main.

Aucune de ces actions n'a été faite par l'assistant : pas de fusion, pas de PR automatique, pas d'installation de poids, pas de modification des réglages GitHub ou Windows.

---

## 6. Commandes de reprise rapide

```powershell
# Rejouer la preuve principale
powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1

# Vérifier les services
docker ps --format "table {{.Names}}\t{{.Status}}"
ollama list

# Interface
cd "C:\factory ai\factory-ui"; npm start

# Évaluation sans effet de bord
python scripts\eval_harness.py list
python scripts\eval_harness.py dry-run

# État du pipeline et audit
docker exec pfe-pg psql -U pfe -d pfe_factory -P pager=off -c "select stage,status,message from pipeline_event order by id desc limit 20;"
docker exec pfe-pg psql -U pfe -d pfe_factory -P pager=off -c "select action,resource from audit_log order by id desc limit 10;"
```

Rappel des ports : API `8081`, RAG `8000`, PostgreSQL `5432`, Ollama `11434`, UI `4200`. Le port `8080` est occupé par Oracle : on ne l'arrête pas.

---

## 7. Conclusion de clôture

Les six parties sont livrées et l'état du prototype est **cohérent avec les documents** : ce qui est annoncé comme vérifié l'est réellement, et ce qui ne l'est pas est explicitement marqué `VERIFY` ou « à faire par toi ».

Le prototype atteint son objectif : **une demande française produit un changement de code vérifié, tracé et proposé à la décision humaine, sans quitter la machine et sans coût de service externe**. Sa valeur tient aux garde-fous déterministes, à l'exécution isolée des tests et au refus de fusionner automatiquement.

La clôture de la rédaction ne signifie pas la fin de l'exploitation : les points V1 à V6 restent des étapes naturelles si tu veux aller plus loin.
