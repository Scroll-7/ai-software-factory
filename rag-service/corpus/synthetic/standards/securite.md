# Standard sécurité local-only (synthétique)

## Interdictions absolues
- Aucun appel à Groq, Gemini, OpenAI, Anthropic ou à un service d'embedding distant.
- Aucune URL sortante (`fetch`, `axios`, `HttpClient`, `requests`) dans le code généré.
- Aucune clé API, aucun token, aucun secret dans le code, les logs ou la réponse JSON.
- Aucune télémétrie : pas d'exporteur Actuator, pas d'analytics Angular, pas de ping serveur.

## Code généré
- Interdits : `eval`, `innerHTML`, `dangerouslySetInnerHTML`, `ProcessBuilder`, `child_process`, `subprocess`, `pickle.loads`.
- Le bac à sable d'exécution tourne avec `--network none`, utilisateur non-root, 2 CPU et 4 Go de RAM.
- Seules les URLs `http://localhost` et `http://127.0.0.1` sont tolérées.

## Journalisation
- Les journaux contiennent des hashes et des identifiants, jamais le texte métier brut ni un CV.
- La table `audit_log` est append-only et chaînée par SHA-256.
