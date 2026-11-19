# Lot 5 — interface web locale (Angular)

## État

✅ **Build vérifié le 2026-11-19** : Angular 21.2.26 + TypeScript 5.9.x sous Node 24.13.0, `npm run build` réussi, page servie sur 4200 sans erreur console, connexion locale testée dans le navigateur. Angular 22 a été écarté : il exige Node ≥ 24.15.0.

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

Les dépendances sont installées et `package-lock.json` est commité. Pour relancer : `npm start`, puis `npm run build` si besoin.
