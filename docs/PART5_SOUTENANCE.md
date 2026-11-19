# Partie 5 — Soutenance fictive

**AI Software Factory — usine logicielle locale et hors ligne**
Version 1.0 — 2026-11-19. Document d'entraînement : les réponses sont ancrées dans le code et les preuves réellement produites. Les hypothèses sont marquées `ASSUMPTION`, les points à confirmer `VERIFY`.

---

## 1. Cadre de la soutenance

Format simulé : **10 minutes de présentation**, puis **questions du jury**. Le jury interroge sur les choix techniques, la conformité au cahier des charges, la sécurité, l'évaluation et les limites. Aucune question ne porte sur une fonctionnalité non livrée : les réponses doivent rester honnêtes.

Règle d'or pour la soutenance : **ne jamais présenter une préparation comme un résultat**. Le harnais d'évaluation est prêt, mais aucun run réel n'a été lancé ; le modèle 3B est déclaré mais inactif.

---

## 2. Fil de présentation (10 minutes)

### 2.1 Accroche (1 min)

« Mon projet transforme une demande métier écrite en français en un changement de code vérifié, proposé à la revue humaine sur GitHub. Tout tourne sur ma machine : pas d'API payante, pas de télémétrie, pas de donnée réelle. Le scénario d'acceptation passe de bout en bout et je peux le rejouer en une commande. »

### 2.2 Contexte et besoin (1 min)

« Le besoin de départ est simple : aller d'une idée métier à un code testé sans dépendre d'un service externe, sans coût récurrent, sans exposer de données. Les contraintes étaient : 0 euro, open-source uniquement, GitHub uniquement, validation humaine obligatoire avant fusion. »

### 2.3 Architecture (2 min)

« Cinq briques. Angular pour l'interface, Spring Boot pour l'orchestration, FastAPI pour le RAG, PostgreSQL avec pgvector pour la mémoire et l'audit, Ollama pour l'inférence. Les tests générés tournent dans Docker avec le réseau coupé. Le diagramme complet est dans la Partie 3. »

### 2.4 Le flux (2 min)

« Une demande crée un run asynchrone. Le RAG fournit un contexte synthétique. Cinq agents — SPECS, DESIGN, CODE, TESTS, REVIEW — produisent du JSON validé. Chaque sortie passe par un parseur défensif, des normalisations déterministes et un validateur métier. En cas d'erreur, on réessaie avec une note de réparation ; si la même signature d'erreur revient, le circuit breaker s'arrête avec `AWAITING_HUMAN`. À la fin, une branche est poussée et une URL de comparaison est renvoyée : je crée la Draft PR à la main, je ne fusionne jamais automatiquement. »

### 2.5 Preuves (2 min)

« Le run `4e428258…` montre SPECS, DESIGN, CODE, TESTS, REVIEW tous verts, puis `AWAITING_REVIEW`. La branche est vérifiée sur le dépôt distant par `git ls-remote`. Le webhook vérifie sa signature HMAC : valide → enregistré, fausse → 401. L'interface compile, est servie sur le port 4200 et la connexion locale fonctionne dans le navigateur. »

### 2.6 Limites et conclusion (2 min)

« Ce que je ne prétends pas : l'évaluation n'a pas été exécutée, la qualité métier n'est pas notée automatiquement, les compteurs de jetons ne sont pas remontés, le webhook ne peut pas recevoir GitHub en 100 % local. Ce prototype vaut par sa traçabilité et ses garde-fous, pas par sa performance brute. Il est rejouable et auditable. »

---

## 3. Questions probables du jury et réponses

### Technique

**Q1 — Pourquoi cinq agents au lieu d'un seul prompt ?**
R. Un 7B local respecte mal un schéma JSON complet sur une longue sortie. Découper en cinq étapes réduit la taille de chaque sortie, permet de valider chaque résultat séparément et de ne réessayer que l'étape en échec. Le coût est plus d'appels locaux, mais ils sont gratuits et la traçabilité est meilleure.

**Q2 — Comment garantissez-vous que le modèle ne fabrique pas une API inexistante ?**
R. Trois filets : le `DomainValidator` refuse les motifs interdits et les chemins hors périmètre, un contrôle statique vérifie les imports ESM, et les tests tournent dans Docker `--network none`. Si le code appelle une bibliothèque absente, le test échoue et déclenche une réparation. **ASSUMPTION :** ce n'est pas une preuve d'absence de bug, mais le dernier filet fiable est l'exécution isolée, pas la confiance dans le modèle.

**Q3 — Pourquoi PostgreSQL et pgvector plutôt qu'un fichier vectoriel ?**
R. La base stocke déjà demandes, runs, artefacts et audit. Garder les embeddings dans la même transaction simplifie la traçabilité et la recherche hybride : similarité cosine `vector(1024)` et `tsvector('french')`. **VERIFY :** la dimension 1024 est figée par BGE-M3 ; la changer imposerait un ré-embedding complet de 20 chunks.

**Q4 — Quelle est la différence entre `AWAITING_REVIEW` et `APPROVED` ?**
R. `AWAITING_REVIEW` signifie seulement que la branche est poussée et que le pipeline attend une décision. `APPROVED` est une décision humaine enregistrée dans la table `approval`. La fusion reste une action manuelle dans GitHub, sans qu'aucun code ne la déclenche.

**Q5 — Comment fonctionne le circuit breaker ?**
R. Le normaliseur d'erreurs extrait une signature stable d'un échec. Si la même signature revient, l'orchestrateur arrête la boucle et passe en `AWAITING_HUMAN` au lieu de consommer toutes les tentatives. Cela évite les boucles coûteuses sur un modèle local buté sur la même erreur.

### Choix et cahier des charges

**Q6 — Le projet est-il vraiment à 0 euro ?**
R. Oui pour les licences, l'inférence et l'hébergement : open-source, Ollama local, machine existante, offre GitHub gratuite. Le vrai coût est mon temps et l'électricité, non mesurés. **ASSUMPTION :** si la machine devait être achetée, le calcul changerait ; elle était déjà disponible.

**Q7 — Pourquoi GitHub et pas GitLab ?**
R. Le cahier des charges l'impose. J'ai écarté GitLab explicitement dans la Partie 3. L'intégration passe par un `ScmClient` avec un dépôt cible par demande, ce qui permet de tester deux dépôts sans changer de code.

**Q8 — Où passe la confidentialité des données ?**
R. Le corpus est synthétique : 6 documents français, 20 chunks, des CV fictifs pour `demo-hr-app`. Les prompts demandent explicitement de ne pas fournir de donnée réelle et les validateurs refusent une question bloquante sur la production ou la confidentialité. Aucun secret n'est commité : le secret JWT est documenté comme « dev only ».

**Q9 — Pourquoi ne pas utiliser Groq, Gemini ou OpenAI ?**
R. Trois raisons : coût, dépendance réseau et exposition des données. Le cahier des charges interdit les API externes. Le code généré est d'ailleurs contrôlé pour ne contenir aucun appel vers ces services (`externalCalls` vide en revue).

**Q10 — L'interface est-elle obligatoire ?**
R. Le cahier des charges demande une interface locale. Angular 21 compile et la connexion par jeton local fonctionne. **VERIFY :** la version est épinglée à cause de Node 24.13.0 sur la machine ; Angular 22 exigerait Node 24.15.0.

### Sécurité et éthique

**Q11 — Le webhook est-il sécurisé ?**
R. Il vérifie `X-Hub-Signature-256` en HMAC-SHA256 avant toute écriture. Une signature fausse renvoie 401, c'est testé. L'événement est tracé dans le journal d'audit chaîné par hachage. **ASSUMPTION :** il ne déclenche aucune action : c'est une simple trace, jamais une fusion.

**Q12 — Comment évitez-vous un score CV discriminatoire ?**
R. Le score est une aide, jamais une décision : aucun attribut protégé n'entre dans le calcul, une checklist de biais est obligatoire pour `demo-hr-app`, l'explication est en français et une validation humaine est tracée. C'est un scénario de démonstration avec des données entièrement fictives.

**Q13 — Quel est le risque principal selon vous ?**
R. Les sorties « presque correctes ». Un modèle local peut produire un code plausible avec un import inexistant ou des clés i18n déséquilibrées. C'est pour cela que j'ai mis des règles déterministes et un test Docker, plutôt que de faire confiance à la revue du modèle lui-même.

### Évaluation

**Q14 — Avez-vous mesuré le gain de temps ?**
R. Pas encore. Le harnais est prêt avec 12 tâches synthétiques et une baseline manuelle à remplir. **VERIFY :** aucun run réel n'a été lancé, parce que chaque run pousse une branche GitHub ; je ne lance pas la matrice de 24 runs sans accord explicite.

**Q15 — Pourquoi comparer un 7B et un 3B ?**
R. Pour observer l'effet de la taille du modèle sur la réussite technique et le nombre de réparations, sur les mêmes tâches. Le 3B est déclaré mais inactif : il faut d'abord que j'installe les poids avec `ollama pull qwen2.5-coder:3b`. Tant que la clé est inactive, l'API répond 400, c'est voulu.

**Q16 — Quel est votre critère de succès ?**
R. Le succès technique est `AWAITING_REVIEW` : la branche est poussée et l'humain peut décider. Ce n'est pas une validation métier. La qualité est évaluée par la revue humaine, pas par une note automatique.

**Q17 — Comment rejouer la preuve ?**
R. Une commande : `powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1`. Elle affiche `RUNNING`, puis `AWAITING_REVIEW` avec l'URL de comparaison, ou `FAILED` avec l'erreur.

### Limites et perspectives

**Q18 — Qu'est-ce qui manque pour un vrai déploiement ?**
R. Une URL publique ou un tunnel pour le webhook, des secrets gérés hors du dépôt, des compteurs de jetons exposés par l'API, une note de qualité automatique et des tests sur plusieurs machines. Rien de tout cela n'est dans le périmètre 100 % local et 0 euro.

**Q19 — Le webhook peut-il recevoir de vrais événements GitHub ?**
R. Non en l'état : GitHub ne peut pas joindre `127.0.0.1`. Il est testable en local avec une signature HMAC calculée à la main. **ASSUMPTION :** le brancher réellement demanderait une URL publique, donc un tunnel ou un hébergement, ce qui sort du cadre retenu.

**Q20 — Si vous recommenciez, que changeriez-vous ?**
R. Je commencerais par exposer les compteurs de jetons et par un schéma de sortie plus strict dès le premier prompt, pour réduire les neuf corrections observées pendant TC-01. Je garderais en revanche le découpage en agents, le test Docker isolé et le journal d'audit : ce sont eux qui rendent le prototype fiable.

---

## 4. Démonstration type (à exécuter par toi)

```powershell
# 1. État des services
docker ps --format "table {{.Names}}\t{{.Status}}"
ollama list

# 2. Rejouer le scénario d'acceptation
powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1

# 3. Montrer la traçabilité
docker exec pfe-pg psql -U pfe -d pfe_factory -P pager=off -c "select stage,status,message from pipeline_event order by id desc limit 20;"

# 4. Montrer l'audit (aucune ligne MERGE/DEPLOY)
docker exec pfe-pg psql -U pfe -d pfe_factory -P pager=off -c "select action,resource from audit_log order by id desc limit 10;"

# 5. Évaluation sans effet de bord
python scripts\eval_harness.py list
python scripts\eval_harness.py dry-run
```

Pendant la démo, bien dire : « je ne fusionne rien, je ne lance aucun run d'évaluation qui pousse une branche. »

---

## 5. Erreurs à ne pas commettre en soutenance

- Présenter le harnais d'évaluation comme exécuté : il est seulement préparé.
- Dire que le 3B est disponible : il est déclaré, inactif, et non installé (`ollama list`).
- Affirmer qu'un Draft PR a été créé automatiquement : sans token, seule une URL de comparaison est renvoyée ; la PR est manuelle.
- Confondre `AWAITING_REVIEW` avec une validation métier.
- Omettre que le webhook public sort du cadre 100 % local.
- Inventer une métrique de temps ou de qualité non mesurée.

---

## 6. Livrables associés

- `docs/PART3_CONCEPTION.md` — conception détaillée.
- `docs/PART4_RAPPORT.md` — rapport de projet.
- `docs/GITHUB_SETUP.md` — réglages manuels GitHub.
- `docs/eval/README.md` — procédure d'évaluation.
- `docs/NEXT_STEPS.md` — étapes restantes à exécuter par l'utilisateur.
