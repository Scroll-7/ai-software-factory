# Standard de tests (synthétique)

## fake-exposer
- Framework : `node:test` (déjà présent). Ne pas ajouter de runner.
- Fichiers : `test/**/*.test.js`.
- Interdits dans les tests : `Date.now`, `Math.random`, réseau, horodatage.
- Chaque test déclare dans son nom ou un commentaire l'exigence `R<n>` couverte.

## demo-hr-app
- Framework : JUnit 5 (Spring Boot Starter Test).
- Fichiers : `src/test/java/**`.
- Un test doit vérifier qu'un score et une explication sont produits.
- Un test doit vérifier que l'interface indique explicitement que la décision finale revient au recruteur.
- Aucune assertion portant sur l'âge, le sexe ou l'origine.

## Critère de sortie
Tous les checks doivent passer avant création de la Draft PR. Un test en échec déclenche au plus une correction automatique, puis escalade humaine.
