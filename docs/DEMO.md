# Démo — AI Software Factory

Parcours complet, prêt à rejouer. Tout est local sauf GitHub (branches et PR). **Aucune fusion, aucun déploiement.**

---

## État actuel (2026-11-19)

| Élément | Valeur |
|---|---|
| PR Draft | [Scroll-7/Fake-exposer#1](https://github.com/Scroll-7/Fake-exposer/pull/1) — badge **Draft** |
| Branche de la PR | `factory/60f0bb90-b295-4ed3-bf9b-405dea333ded/e87a5526-d65e-4236-8b2b-f496a296e0c2` |
| Commit de la PR | `cbadcb4` (CI générée corrigée) |
| Run vert de référence | `098b26f5-58d3-4dce-a88c-6a37871a4aa1` |
| Original | `C:\factory ai\workspaces\Fake-exposer` sur `main` (intact) |
| Copie générée | `C:\factory ai\workspaces\Fake-exposer-upgraded` sur la branche de la PR |
| Ports | UI usine `4200`, API `8081`, usine RAG `8000`, SQL `5432`, Ollama `11434`, site généré `3002` |

---

## 1. Démo rapide — preuve du pipeline (5 min, sans effet de bord)

Dans un PowerShell, place-toi dans `C:\factory ai` :

```powershell
$t = python scripts\dev_token.py
$h = @{ Authorization = "Bearer $t" }
$r = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8081/api/v1/runs/098b26f5-58d3-4dce-a88c-6a37871a4aa1/events" -Headers $h
[System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) | ConvertFrom-Json | ConvertTo-Json -Depth 5
```

Tu vois : `SPECS → DESIGN → CODE → TESTS → REVIEW → GITHUB_PR / AWAITING_HUMAN`.

> Le décodage UTF-8 explicite évite le mojibake (`gÃ©nÃ©rÃ©s`) de `Invoke-RestMethod` sous Windows PowerShell. Les données sont correctes en base.

Traçabilité et audit :

```powershell
docker exec pfe-pg psql -U pfe -d pfe_factory -P pager=off -c "select stage,status,message from pipeline_event order by id desc limit 8;"
docker exec pfe-pg psql -U pfe -d pfe_factory -P pager=off -c "select action,resource from audit_log order by id desc limit 10;"
```

Aucune ligne `MERGE` ni `DEPLOY` : la fusion reste humaine.

---

## 2. Copie générée / version « upgrade »

### Dans GitHub

1. Ouvre la [PR #1](https://github.com/Scroll-7/Fake-exposer/pull/1).
2. Onglet **Files changed** : 4 fichiers ajoutés.
   - `public/i18n/fr.json`, `public/i18n/en.json`
   - `test/i18n.test.js`
   - `.github/workflows/factory-generated-ci.yml` (à côté du `ci.yml` existant, rien n'est écrasé)
3. `main` est intact : c'est bien une copie parallèle.
4. **Ne pas** cliquer sur « Ready for review » ni fusionner.

### En local

L'original est sur `main` dans `workspaces\Fake-exposer`. La copie générée est dans un worktree séparé :

```powershell
git -C "C:\factory ai\workspaces\Fake-exposer" worktree add "C:\factory ai\workspaces\Fake-exposer-upgraded" <branche-de-la-PR>
cd "C:\factory ai\workspaces\Fake-exposer-upgraded"
npm ci
$env:PORT=3002; npm start
```

Site généré : `http://127.0.0.1:3002` (HTTP 200 vérifié). L'original peut tourner sur `3001` si tu installs ses dépendances.

Test généré, sans dépendance :

```powershell
cd "C:\factory ai\workspaces\Fake-exposer-upgraded"
node --test test/i18n.test.js
```

Résultat réel : **4 tests passés, 0 échec**.

---

## 3. Résultat de la CI sur la PR #1 (vérifié via API GitHub)

| Job | Résultat |
|---|---|
| `Tests node:test (déterministes, hors ligne)` — workflow **généré** | ✅ success |
| `Vérification syntaxique Python (local uniquement)` — workflow **généré** | ✅ success |
| `Eval harness self-tests` — workflow d'origine | ✅ success |
| `Node tests + lint` — workflow d'origine | ❌ failure (exit code 9) |

**Le job `Node tests + lint` échoue déjà sur `main`** (commit `06a7542`, vérifié via l'API GitHub). Ce n'est donc **pas** une régression du code généré. En local, `npm run lint` et `npm test` passent (224 tests). Je ne modifie pas le `ci.yml` d'origine : l'original doit rester intact.

**Conséquence pour la protection de branche** : n'exige pas `Node tests + lint` comme check requis tant que ce problème d'origine n'est pas réglé. Exige `tests-node` (workflow généré) à la place.

## 4. Important : ce que la démo ne montre pas

- Le run TC-01 n'a **pas modifié** `index.html`, `style.css` ni `app.js`. Les fichiers i18n sont ajoutés mais pas encore branchés dans l'application : **l'apparence et le comportement du site sont identiques** à l'original.
- La « version upgrade » se vérifie donc dans ses **fichiers, ses tests et sa CI**, pas à l'écran.
- Pour une différence visible, il faut demander à l'usine une tâche qui modifie `public/index.html` ou `public/js/app.js`, puis relancer un run.

---

---

## 5. Démo en direct (facultatif, pousse une branche)

À lancer depuis l'UI (`http://127.0.0.1:4200` → Se connecter → formulaire) ou en PowerShell :

```powershell
powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1
```

Tu vois `RUNNING` puis `AWAITING_REVIEW` avec une URL de comparaison. Ensuite, crée une Draft PR à la main (flèche ▾ à côté de « Create pull request » → « Create draft pull request »).

---

## 6. À ne jamais faire pendant la démo

- Ne pas fusionner la PR.
- Ne pas lancer `eval_harness.py run` (chaque run pousse une branche).
- Ne pas activer le 3B ni faire `ollama pull` sans accord explicite.
- Ne pas exposer le webhook sur une URL publique (hors cadre 100 % local).

---

## 7. Soutenance

Fil de présentation, 20 questions/réponses et erreurs à éviter : `docs/PART5_SOUTENANCE.md`.
