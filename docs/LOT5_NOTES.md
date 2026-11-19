# Lot 5 — interface web locale (Angular)

## État

Structure de l'application créée dans `factory-ui/` :

- `package.json`, `angular.json`, `tsconfig*.json` : Angular 22 autonome, sans analyse, build local.
- `src/app/app.component.ts` : formulaire de demande métier, connexion locale, suivi de l'exécution, décision humaine.
- `src/app/app.component.html` / `.css` : interface française, une seule page, quatre sections numérotées.
- `src/styles.css` : style minimal.

## Backend ajouté pour l'UI

- `POST /api/v1/auth/dev-token` : renvoie un JWT local de développement (même secret que `scripts/dev_token.py`, aucun service externe).
- CORS autorisé uniquement depuis `http://localhost:4200` et `http://127.0.0.1:4200` pour `/api/**`.

## Flux attendu

1. Connexion : le jeton est généré par le bouton (ou saisi manuellement).
2. Demande métier : titre + texte français + cible + dépôt + langue.
3. Suivi : `GET /runs/{id}/execution` et `GET /runs/{id}/events` toutes les 5 secondes.
4. Validation humaine : `POST /runs/{id}/approve` uniquement quand le statut est `AWAITING_REVIEW`.
   La fusion reste manuelle dans GitHub.

## Pour toi — une étape à la fois

```powershell
cd "C:\factory ai\factory-ui"
npm install
```

Puis, quand l'installation est terminée :

```powershell
npm start
```

Ouvre `http://127.0.0.1:4200`. L'API doit être démarrée sur `8081`.

VERIFY : versions Angular 22.3 / TypeScript 6.0 vérifiées avec `npm view` le 2026-11-19 ; le build n'a pas encore été exécuté (installation en attente de ton accord).
