-- Données locales de démarrage — AUCUN secret réel, AUCUNE donnée confidentielle.
-- Idempotent : relancé à chaque démarrage de l'API sans écraser les données existantes.

INSERT INTO app_user(id, username, full_name, role, password_hash)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'requester', 'Utilisateur de test local', 'REQUESTER', 'local-dev-only-not-used'),
  ('22222222-2222-2222-2222-222222222222', 'reviewer', 'Reviseur de test local', 'REVIEWER', 'local-dev-only-not-used'),
  ('33333333-3333-3333-3333-333333333333', 'admin', 'Administrateur de test local', 'ADMIN', 'local-dev-only-not-used')
ON CONFLICT (id) DO NOTHING;;

-- Les clés sont celles utilisées par FactoryController.create().
INSERT INTO model_config(key, kind, provider, model_name, endpoint, quantization, context_window, dim, temperature, is_active)
VALUES
  ('ollama-local', 'LLM', 'ollama', 'qwen2.5-coder:7b', 'http://localhost:11434', 'Q4_K_M', 4096, NULL, 0.000, true),
  ('ollama-embed-local', 'EMBEDDING', 'ollama', 'bge-m3', 'http://localhost:11434', NULL, 8192, 1024, NULL, true)
ON CONFLICT (key) DO NOTHING;;
