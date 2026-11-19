-- Schéma idempotent pour le prototype local.
-- VERIFIER: nom de l'opclass HNSW avant la 1re ingestion.
-- Dimension VERIFIEE le 2026-11-18 : BGE-M3 (ollama `bge-m3`) produit 1024 dimensions
-- (source : fiche officielle BAAI/bge-m3). Ne PAS changer après le premier INSERT :
-- cela imposerait un re-embedding complet.

CREATE EXTENSION IF NOT EXISTS vector;;
CREATE EXTENSION IF NOT EXISTS pgcrypto;;

-- Les blocs DO tiennent sur UNE ligne : le séparateur Spring est `;;`, les `;` internes
-- ne doivent donc jamais finir une ligne (sinon Spring couperait le bloc en deux).
DO $$ BEGIN CREATE TYPE request_status AS ENUM ('NEW','RUNNING','AWAITING_REVIEW','APPROVED','REJECTED','MERGED','DEPLOYED','FAILED'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;;

DO $$ BEGIN CREATE TYPE stage_name AS ENUM ('SPECS','DESIGN','CODE','TESTS','REVIEW','GITLAB_MR','GITHUB_PR','CI','STAGING'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;;
-- Migration idempotente pour une base déjà créée (ancien libellé GITLAB_MR conservé).
ALTER TYPE stage_name ADD VALUE IF NOT EXISTS 'GITHUB_PR';;

DO $$ BEGIN CREATE TYPE stage_status AS ENUM ('PENDING','RUNNING','SUCCEEDED','FAILED','SKIPPED','AWAITING_HUMAN'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;;

DO $$ BEGIN CREATE TYPE artifact_kind AS ENUM ('SPEC_JSON','ARCHITECTURE_JSON','PATCH','FILE','TEST_FILE','REVIEW_JSON'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;;

DO $$ BEGIN CREATE TYPE review_decision AS ENUM ('APPROVED','CHANGES_REQUESTED','REJECTED'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;;

CREATE TABLE IF NOT EXISTS app_user (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  username      text NOT NULL UNIQUE,
  full_name     text NOT NULL,
  role          text NOT NULL CHECK (role IN ('REQUESTER','REVIEWER','ADMIN')),
  password_hash text NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now()
);;

CREATE TABLE IF NOT EXISTS model_config (
  key            text PRIMARY KEY,
  kind           text NOT NULL CHECK (kind IN ('LLM','EMBEDDING')),
  provider       text NOT NULL DEFAULT 'ollama',
  model_name     text NOT NULL,
  endpoint       text NOT NULL,
  quantization   text,
  context_window integer,
  dim            integer,
  temperature    numeric(4,3),
  is_active      boolean NOT NULL DEFAULT false,
  created_at     timestamptz NOT NULL DEFAULT now()
);;

CREATE TABLE IF NOT EXISTS change_request (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title         text NOT NULL,
  business_text text NOT NULL,
  target_app    text NOT NULL CHECK (target_app IN ('fake-exposer','demo-hr-app')),
  target_repo   text NOT NULL,
  locale        text NOT NULL DEFAULT 'fr' CHECK (locale IN ('fr','en','both')),
  status        request_status NOT NULL DEFAULT 'NEW',
  requester_id  uuid NOT NULL REFERENCES app_user(id),
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);;
CREATE INDEX IF NOT EXISTS idx_change_request_status ON change_request(status, created_at DESC);;

CREATE TABLE IF NOT EXISTS requirement_item (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id          uuid NOT NULL REFERENCES change_request(id) ON DELETE CASCADE,
  ordinal             integer NOT NULL,
  external_ref        text,
  text                text NOT NULL,
  acceptance_criteria jsonb NOT NULL DEFAULT '[]'::jsonb,
  source_snippet      text,
  UNIQUE (request_id, ordinal)
);;

CREATE TABLE IF NOT EXISTS generation_run (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id          uuid NOT NULL REFERENCES change_request(id) ON DELETE CASCADE,
  attempt             integer NOT NULL DEFAULT 1,
  status              stage_status NOT NULL DEFAULT 'PENDING',
  current_stage       stage_name,
  llm_model_key       text NOT NULL REFERENCES model_config(key),
  embedding_model_key text NOT NULL REFERENCES model_config(key),
  prompt_version      text NOT NULL,
  git_branch          text,
  mr_iid              integer,
  mr_url              text,
  config              jsonb NOT NULL DEFAULT '{}'::jsonb,
  started_at          timestamptz,
  finished_at         timestamptz
);;
CREATE INDEX IF NOT EXISTS idx_run_request ON generation_run(request_id, started_at DESC);;

CREATE TABLE IF NOT EXISTS pipeline_event (
  id         bigserial PRIMARY KEY,
  run_id     uuid NOT NULL REFERENCES generation_run(id) ON DELETE CASCADE,
  stage      stage_name NOT NULL,
  status     stage_status NOT NULL,
  message    text,
  payload    jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);;
CREATE INDEX IF NOT EXISTS idx_event_run ON pipeline_event(run_id, created_at);;

CREATE TABLE IF NOT EXISTS agent_output (
  id                bigserial PRIMARY KEY,
  run_id            uuid NOT NULL REFERENCES generation_run(id) ON DELETE CASCADE,
  stage             stage_name NOT NULL,
  attempt           integer NOT NULL,
  prompt_version    text NOT NULL,
  model_key         text NOT NULL REFERENCES model_config(key),
  input_ref         text NOT NULL,
  output            jsonb NOT NULL,
  tokens_in         integer,
  tokens_out        integer,
  latency_ms        integer,
  validated         boolean NOT NULL DEFAULT false,
  validation_errors jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at        timestamptz NOT NULL DEFAULT now()
);;
CREATE INDEX IF NOT EXISTS idx_agent_output_run ON agent_output(run_id, stage, attempt);;

CREATE TABLE IF NOT EXISTS artifact (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id        uuid NOT NULL REFERENCES generation_run(id) ON DELETE CASCADE,
  stage         stage_name NOT NULL,
  kind          artifact_kind NOT NULL,
  path          text NOT NULL,
  content_sha256 char(64) NOT NULL,
  content       text NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (run_id, stage, path, content_sha256)
);;

CREATE TABLE IF NOT EXISTS check_result (
  id          bigserial PRIMARY KEY,
  run_id      uuid NOT NULL REFERENCES generation_run(id) ON DELETE CASCADE,
  stage       stage_name NOT NULL,
  check_type  text NOT NULL CHECK (check_type IN
              ('COMPILE','UNIT_TEST','LINT','SAST','DIFF','POLICY')),
  command     text NOT NULL,
  exit_code   integer NOT NULL,
  stdout_tail text,
  stderr_tail text,
  duration_ms integer,
  created_at  timestamptz NOT NULL DEFAULT now()
);;

CREATE TABLE IF NOT EXISTS traceability_link (
  id             bigserial PRIMARY KEY,
  run_id         uuid NOT NULL REFERENCES generation_run(id) ON DELETE CASCADE,
  requirement_id uuid NOT NULL REFERENCES requirement_item(id),
  artifact_id    uuid NOT NULL REFERENCES artifact(id),
  file_path      text NOT NULL,
  line_range     text,
  rationale      text,
  UNIQUE (run_id, requirement_id, artifact_id, file_path)
);;

CREATE TABLE IF NOT EXISTS approval (
  id          bigserial PRIMARY KEY,
  run_id      uuid NOT NULL REFERENCES generation_run(id) ON DELETE CASCADE,
  approver_id uuid NOT NULL REFERENCES app_user(id),
  decision    review_decision NOT NULL,
  comment     text,
  decided_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (run_id, approver_id)
);;

CREATE TABLE IF NOT EXISTS audit_log (
  id         bigserial PRIMARY KEY,
  run_id     uuid REFERENCES generation_run(id) ON DELETE SET NULL,
  actor      text NOT NULL,
  action     text NOT NULL,
  resource   text NOT NULL,
  metadata   jsonb NOT NULL DEFAULT '{}'::jsonb,
  prev_hash  char(64),
  hash       char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);;
CREATE INDEX IF NOT EXISTS idx_audit_run ON audit_log(run_id, created_at);;

CREATE TABLE IF NOT EXISTS rag_document (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_uri    text NOT NULL,
  title         text NOT NULL,
  kind          text NOT NULL CHECK (kind IN
                ('STANDARD','EXAMPLE_CODE','ADR','README','TEST_TEMPLATE')),
  language      text NOT NULL DEFAULT 'fr' CHECK (language IN ('fr','en')),
  version       text NOT NULL,
  content_sha256 char(64) NOT NULL,
  is_active     boolean NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (source_uri, version)
);;

CREATE TABLE IF NOT EXISTS rag_chunk (
  id          bigserial PRIMARY KEY,
  document_id uuid NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
  ordinal     integer NOT NULL,
  content     text NOT NULL,
  token_count integer NOT NULL,
  metadata    jsonb NOT NULL DEFAULT '{}'::jsonb,
  content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('french', content)) STORED,
  embedding   vector(1024) NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (document_id, ordinal)
);;
CREATE INDEX IF NOT EXISTS idx_rag_chunk_embedding
  ON rag_chunk USING hnsw (embedding vector_cosine_ops);;
CREATE INDEX IF NOT EXISTS idx_rag_chunk_tsv ON rag_chunk USING gin (content_tsv);;
CREATE INDEX IF NOT EXISTS idx_rag_chunk_doc ON rag_chunk(document_id);;

ANALYZE rag_chunk;;
