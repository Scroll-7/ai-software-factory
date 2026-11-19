package com.pfe.factory.api;

import com.pfe.factory.audit.AuditService;
import com.pfe.factory.config.FactoryProperties;
import com.pfe.factory.domain.RunRepository;
import com.pfe.factory.workflow.ExecutionRegistry;
import com.pfe.factory.workflow.GenerationOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
public class FactoryController {
    private final RunRepository repo;
    private final GenerationOrchestrator orchestrator;
    private final JdbcTemplate jdbc;
    private final FactoryProperties props;
    private final AuditService audit;
    private final ExecutionRegistry executions;

    public FactoryController(RunRepository repo, GenerationOrchestrator orchestrator,
                             JdbcTemplate jdbc, FactoryProperties props, AuditService audit,
                             ExecutionRegistry executions) {
        this.repo = repo;
        this.orchestrator = orchestrator;
        this.jdbc = jdbc;
        this.props = props;
        this.audit = audit;
        this.executions = executions;
    }

    public record NewRequest(String title, String businessText, String targetApp,
                             String targetRepo, String locale, UUID requesterId) {}

    public record Approval(String decision, String comment, UUID approverId) {}

    @PostMapping("/change-requests")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody NewRequest body) {
        String repoName = body.targetRepo() != null ? body.targetRepo() : defaultRepoFor(body.targetApp());
        UUID requestId = repo.createRequest(body.title(), body.businessText(), body.targetApp(),
                repoName, body.locale() == null ? "fr" : body.locale(), body.requesterId());
        UUID runId = repo.createRun(requestId, "ollama-local", "ollama-embed-local",
                props.prompts().version());
        audit.append(runId, "requester", "REQUEST_CREATED", requestId.toString(),
                Map.of("targetApp", body.targetApp()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", requestId);
        out.put("runId", runId);
        out.put("status", "NEW");
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @PostMapping("/runs/{runId}/execute")
    public ResponseEntity<Map<String, Object>> execute(@PathVariable UUID runId) {
        if ("RUNNING".equals(executions.get(runId).status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("runId", runId, "status", "RUNNING",
                            "message", "exécution déjà en cours"));
        }
        executions.start(runId);
        CompletableFuture.runAsync(() -> {
            try {
                executions.done(runId, orchestrator.execute(runId));
            } catch (Throwable t) {
                executions.fail(runId, t);
            }
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", "RUNNING");
        out.put("pollUrl", "/api/v1/runs/" + runId + "/execution");
        return ResponseEntity.accepted().body(out);
    }

    @GetMapping("/runs/{runId}/execution")
    public Map<String, Object> execution(@PathVariable UUID runId) {
        var state = executions.get(runId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", state.status());
        if (state.prUrl() != null) {
            out.put("pullRequest", Map.of("number", state.prNumber(),
                    "url", state.prUrl(), "state", "draft"));
        }
        if (state.error() != null) out.put("error", state.error());
        return out;
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> run(@PathVariable UUID runId) {
        return repo.run(runId);
    }

    @GetMapping("/runs/{runId}/events")
    public List<Map<String, Object>> events(@PathVariable UUID runId) {
        return repo.events(runId);
    }

    @GetMapping("/runs/{runId}/audit")
    public List<Map<String, Object>> audit(@PathVariable UUID runId) {
        return jdbc.queryForList("select id,actor,action,resource,metadata,created_at from audit_log "
                + "where run_id=? order by id", runId);
    }

    @PostMapping("/runs/{runId}/approve")
    public Map<String, Object> approve(@PathVariable UUID runId, @Valid @RequestBody Approval body) {
        // L'agent REVIEW utilise APPROVE/REJECT ; la table SQL utilise APPROVED/REJECTED.
        String decision = switch (body.decision()) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            default -> body.decision();
        };
        jdbc.update("insert into approval(run_id,approver_id,decision,comment) values(?,?,?::review_decision,?)",
                runId, body.approverId(), decision, body.comment());
        audit.append(runId, body.approverId().toString(), "HUMAN_APPROVAL", runId.toString(),
                Map.of("decision", decision));
        return Map.of("runId", runId, "decision", decision,
                "nextStep", "Fusionne la PR manuellement dans GitHub.");
    }

    private String defaultRepoFor(String targetApp) {
        return "fake-exposer".equals(targetApp)
                ? props.scm().fakeExposerRepo() : props.scm().defaultRepo();
    }
}
