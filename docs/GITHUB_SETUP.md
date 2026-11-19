# GitHub — réglages manuels (Lot 4)

Tout ce qui suit est **à faire par toi dans GitHub**. L'assistant ne modifie jamais la protection de branche, ne fusionne rien et ne crée pas de PR automatiquement.

## 1. Draft PR (sans token)

Quand un run termine avec `AWAITING_REVIEW`, l'API renvoie une URL de comparaison GitHub. Ouvre-la, puis :

1. **Create pull request**.
2. Choisis **Draft pull request** (pas « Ready for review »).
3. Vérifie que la cible est bien `main` (ou la branche par défaut) de `Scroll-7/Fake-exposer`.
4. Ne fusionne pas : la décision finale est humaine.

`PFE_GITHUB_TOKEN` reste optionnel. Sans lui, aucun appel à l'API GitHub n'est fait.

## 2. Protection de la branche cible

Dans `Scroll-7/Fake-exposer` → **Settings → Branches → Add branch ruleset** (ou *Branch protection rule*) :

- Branche cible : `main`.
- ✅ Require a pull request before merging.
- ✅ Require approvals : **1**.
- ✅ Dismiss stale approvals when new commits are pushed.
- ✅ Require status checks to pass : coche le job `tests-node` du workflow généré `factory-generated-ci.yml`.
- ✅ Block force pushes et block deletions.
- ❌ Ne coche pas « Automatically merge approved pull requests ».
- ✅ Allow force pushes : uniquement pour toi si besoin, jamais pour l'automatisation.

ASSUMPTION : les noms exacts des réglages varient selon les versions GitHub. **VERIFY** visuellement dans ton interface.

## 3. Webhook local

L'API expose `POST /api/v1/webhooks/github`. Elle vérifie `X-Hub-Signature-256` (HMAC-SHA256) avec le secret local `factory.github.webhook-secret`, puis écrit un événement `GITHUB_WEBHOOK` dans `audit_log`. Aucun appel sortant.

Secret par défaut (développement uniquement) : `local-dev-only-webhook-secret`. Surcharge : variable d'environnement `PFE_GITHUB_WEBHOOK_SECRET`. Ne le commite jamais.

```powershell
$secret = 'local-dev-only-webhook-secret'
$body = '{"action":"opened","pull_request":{"number":1,"draft":true,"html_url":"https://github.com/Scroll-7/Fake-exposer/pull/1","head":{"ref":"factory/test"}},"repository":{"full_name":"Scroll-7/Fake-exposer"}}'
$hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($secret))
$sig = 'sha256=' + [BitConverter]::ToString($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($body))).Replace('-','').ToLower()
Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/webhooks/github' -Method Post -Headers @{'X-GitHub-Event'='pull_request';'X-GitHub-Delivery'='test-local-1';'X-Hub-Signature-256'=$sig} -Body $body -ContentType 'application/json'
```

Réponse attendue : `recorded: true`, `event: pull_request`. Une signature fausse donne **401**.

**ASSUMPTION / VERIFY :** GitHub ne peut pas appeler `127.0.0.1`. Pour recevoir de vrais événements, il faut une URL publiquement joignable, donc un tunnel ou un hébergement — ce qui sort du cadre « 100 % local, $0 ». Le webhook est donc **testable en local mais optionnel** pour le prototype. Si tu le branches plus tard : *Settings → Webhooks → Add webhook*, content type `application/json`, secret identique, événements « Pull requests » uniquement.

## 4. Vérification après un run

```powershell
docker exec pfe-pg psql -U pfe -d pfe_factory -P pager=off -c "select action,resource,created_at from audit_log where action in ('GITHUB_WEBHOOK','HUMAN_APPROVAL') order by id desc limit 10;"
```

Aucune ligne `MERGE` ou `DEPLOY` ne doit apparaître : la fusion se fait uniquement dans l'interface GitHub.
