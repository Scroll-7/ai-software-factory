package com.pfe.factory.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.factory.util.Hashes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class RunRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RunRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public UUID createRequest(String title, String businessText, String targetApp,
                              String targetRepo, String locale, UUID requesterId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into change_request(id,title,business_text,target_app,target_repo,locale,requester_id) "
                        + "values(?,?,?,?,?,?,?)",
                id, title, businessText, targetApp, targetRepo, locale, requesterId);
        return id;
    }

    public UUID createRun(UUID requestId, String llmKey, String embeddingKey, String promptVersion) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into generation_run(id,request_id,llm_model_key,embedding_model_key,prompt_version,status,started_at) "
                        + "values(?,?,?,?,?,'RUNNING',now())",
                id, requestId, llmKey, embeddingKey, promptVersion);
        return id;
    }

    public Map<String, Object> run(UUID runId) {
        return jdbc.queryForMap(
                "select r.*, c.title, c.business_text, c.target_app, c.target_repo "
                        + "from generation_run r join change_request c on c.id = r.request_id where r.id = ?",
                runId);
    }

    public String requestText(UUID runId) {
        return jdbc.queryForObject(
                "select c.business_text from generation_run r join change_request c on c.id=r.request_id "
                        + "where r.id=?", String.class, runId);
    }

    public void saveRequirements(UUID runId, JsonNode requirements) {
        Map<String, Object> r = run(runId);
        int i = 0;
        for (JsonNode req : requirements) {
            i++;
            try {
                jdbc.update("insert into requirement_item(request_id,ordinal,external_ref,text,acceptance_criteria,source_snippet) "
                                + "values(?,?,?,?,?::jsonb,?) "
                        + "on conflict (request_id, ordinal) do update set "
                        + "external_ref=excluded.external_ref, text=excluded.text, "
                        + "acceptance_criteria=excluded.acceptance_criteria, "
                        + "source_snippet=excluded.source_snippet",
                        r.get("request_id"), i,
                        req.path("id").asText(),
                        req.path("text").asText(),
                        mapper.writeValueAsString(req.path("acceptanceCriteria")),
                        String.join(", ", textArray(req.path("sourceUris"))));
            } catch (Exception e) {
                throw new IllegalStateException("sauvegarde exigence impossible", e);
            }
        }
    }

    public List<String> requirementIds(UUID runId) {
        return jdbc.queryForList(
                "select external_ref from requirement_item ri join change_request c on c.id=ri.request_id "
                        + "join generation_run r on r.request_id=c.id where r.id=? order by ri.ordinal",
                String.class, runId);
    }

    public void saveAgentOutput(UUID runId, String stage, int attempt, JsonNode output,
                                List<String> errors, String inputRef, String modelKey,
                                int tokensIn, int tokensOut, long latencyMs) {
        try {
            jdbc.update("insert into agent_output(run_id,stage,attempt,prompt_version,model_key,input_ref,output,"
                            + "tokens_in,tokens_out,latency_ms,validated,validation_errors) "
                            + "values(?,?::stage_name,?,?,?,?,?::jsonb,?,?,?,?,?::jsonb)",
                    runId, stage, attempt, "v1", modelKey, inputRef,
                    mapper.writeValueAsString(output), tokensIn, tokensOut, latencyMs,
                    errors.isEmpty(), mapper.writeValueAsString(errors));
        } catch (Exception e) {
            throw new IllegalStateException("sauvegarde agent_output impossible", e);
        }
    }

    public void saveArtifacts(UUID runId, String stage, String kind, List<Model.GeneratedFile> files) {
        for (Model.GeneratedFile f : files) {
            jdbc.update("insert into artifact(run_id,stage,kind,path,content_sha256,content) values(?,?::stage_name,?::artifact_kind,?,?,?) "
                            + "on conflict do nothing",
                    runId, stage, kind, f.path(), Hashes.sha256(f.content()), f.content());
        }
    }

    public void event(UUID runId, String stage, String status, String message) {
        jdbc.update("insert into pipeline_event(run_id,stage,status,message) values(?,?::stage_name,?::stage_status,?)",
                runId, stage, status, message);
    }

    public List<Map<String, Object>> events(UUID runId) {
        return jdbc.queryForList("select * from pipeline_event where run_id=? order by created_at", runId);
    }

    private List<String> textArray(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) n.forEach(x -> out.add(x.asText()));
        return out;
    }
}
