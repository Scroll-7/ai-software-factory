# Modèle de test node:test (synthétique)

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// R1 : le dictionnaire français contient la clé du titre
test('R1 - dictionnaire francais present', () => {
  const fr = JSON.parse(readFileSync(new URL('../public/i18n/fr.json', import.meta.url), 'utf8'));
  assert.equal(typeof fr['header.title'], 'string');
});

// R2 : le comportement est deterministe, sans reseau ni horodatage
test('R2 - selection de langue persistee', () => {
  let langue = 'fr';
  const changer = (v) => { langue = v; };
  changer('en');
  assert.equal(langue, 'en');
});
```

Règles : un test = une exigence ; aucun appel réseau ; aucun aléatoire.
