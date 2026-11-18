package com.pfe.factory.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.factory.audit.AuditService;
import com.pfe.factory.config.FactoryProperties;
import com.pfe.factory.domain.Model;
import com.pfe.factory.domain.RunRepository;
import com.pfe.factory.ai.AgentGateway;
import com.pfe.factory.ai.PromptStore;
import com.pfe.factory.ports.CheckClient;
import com.pfe.factory.ports.RagClient;
import com.pfe.factory.ports.RepoClient;
import com.pfe.factory.ports.ScmClient;
import com.pfe.factory.util.Hashes;
import com.pfe.factory.validation.DomainValidator;
import com.pfe.factory.validation.ErrorDigest;
import com.pfe.factory.validation.MiniJsonSchemaValidator;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Boucle generate / check / retry. Les effets de bord passent uniquement par les ports. */
@Service
public class GenerationOrchestrator {
    private final ObjectMapper mapper;
    private final FactoryProperties props;
    private final PromptStore prompts;
    private final AgentGateway gateway;
    private final MiniJsonSchemaValidator schemaValidator;
    private final DomainValidator domainValidator;
    private final RunRepository repo;
    private final AuditService audit;
    private final RagClient ragClient;
    private final RepoClient repoClient;
    private final CheckClient checkClient;
    private final ScmClient scmClient;

    public GenerationOrchestrator(ObjectMapper mapper, FactoryProperties props, PromptStore prompts,
                                  AgentGateway gateway, MiniJsonSchemaValidator schemaValidator,
                                  DomainValidator domainValidator, RunRepository repo, AuditService audit,
                                  RagClient ragClient, RepoClient repoClient, CheckClient checkClient,
                                  ScmClient scmClient) {
        this.mapper = mapper;
        this.props = props;
        this.prompts = prompts;
        this.gateway = gateway;
        this.schemaValidator = schemaValidator;
        this.domainValidator = domainValidator;
        this.repo = repo;
        this.audit = audit;
        this.ragClient = ragClient;
        this.repoClient = repoClient;
        this.checkClient = checkClient;
        this.scmClient = scmClient;
    }

    public Model.PrRef execute(UUID runId) throws Exception {
        Map<String, Object> run = repo.run(runId);
        String targetApp = String.valueOf(run.get("target_app"));
        String repoName = String.valueOf(run.get("target_repo"));
        String modelKey = String.valueOf(run.get("llm_model_key"));
        String businessText = repo.requestText(runId);

        List<Model.Chunk> chunks = ragClient.search(targetApp + " standard local", "STANDARD", 5);
        String ragContext = ragContext(chunks);
        List<String> tree = repoClient.tree(repoName, "HEAD");
        String repoContext = repoContext(repoName, tree);

        DomainValidator.Context initialCtx = new DomainValidator.Context(
                chunks.stream().map(Model.Chunk::sourceUri).collect(Collectors.toSet()),
                Set.copyOf(tree), Set.of(), Set.of(), targetApp);

        Map<String, String> base = new HashMap<>();
        base.put("RAG_CONTEXT", ragContext);
        base.put("REPO_CONTEXT", repoContext);
        base.put("TARGET_APP", targetApp);

        Map<String, String> specsValues = new HashMap<>(base);
        specsValues.put("BUSINESS_TEXT", businessText);
        JsonNode specs = callStage("SPECS", runId, specsValues, initialCtx, modelKey);
        repo.saveRequirements(runId, specs.path("requirements"));
        repo.event(runId, "SPECS", "SUCCEEDED", "exigences extraites");

        List<String> reqIds = repo.requirementIds(runId);
        DomainValidator.Context ctx = new DomainValidator.Context(
                initialCtx.allowedSourceUris(), Set.copyOf(tree), Set.of(),
                Set.copyOf(reqIds), targetApp);

        Map<String, String> designValues = new HashMap<>(base);
        designValues.put("SPECS_JSON", specs.toString());
        JsonNode design = callStage("DESIGN", runId, designValues, ctx, modelKey);
        Set<String> newPaths = new HashSet<>();
        for (JsonNode f : design.path("filePlan")) {
            if ("CREATE".equals(f.path("action").asText())) newPaths.add(f.path("path").asText());
        }
        ctx = new DomainValidator.Context(ctx.allowedSourceUris(), ctx.knownPaths(),
                newPaths, ctx.requirementIds(), targetApp);
        repo.event(runId, "DESIGN", "SUCCEEDED", "plan de " + design.path("filePlan").size() + " fichiers");

        List<Model.GeneratedFile> generated = new ArrayList<>();
        Map<String, String> firstCodeValues = null;
        for (JsonNode plan : design.path("filePlan")) {
            String path = plan.path("path").asText();
            Map<String, String> codeValues = new HashMap<>(base);
            codeValues.put("FILE_PATH", path);
            codeValues.put("ACTION", plan.path("action").asText());
            codeValues.put("REQUIREMENT_IDS", String.join(", ", textArray(plan.path("requirementIds"))));
            codeValues.put("CURRENT_FILE", "CREATE".equals(plan.path("action").asText())
                    ? "" : repoClient.read(repoName, path));
            codeValues.put("FEW_SHOT_EXAMPLE", "// Exemple issu de synthetic://templates (à brancher Lot 2)");
            if (firstCodeValues == null) firstCodeValues = new HashMap<>(codeValues);
            JsonNode code = callStage("CODE", runId, codeValues, ctx, modelKey);
            generated.addAll(toFiles(code));
        }
        repo.saveArtifacts(runId, "CODE", "FILE", generated);
        repo.event(runId, "CODE", "SUCCEEDED", generated.size() + " fichiers générés");

        Model.CheckResult result = checkClient.applyAndCheck(runId, generated);
        Set<String> seenSignatures = new HashSet<>();
        int repairs = 0;
        while (!result.ok()) {
            ErrorDigest digest = ErrorDigest.from(result.stdout(), result.stderr(), "<repo>", result.checkType());
            repo.event(runId, "CODE", "FAILED", "signature " + digest.signature());
            audit.append(runId, "system", "CHECK_FAILED", runId + "/" + digest.signature(),
                    Map.of("type", digest.checkType(), "messages", digest.messages().size()));
            if (!seenSignatures.add(digest.signature())
                    || repairs >= props.limits().maxRepairsPerRun()) {
                repo.event(runId, "CODE", "AWAITING_HUMAN", "erreur répétée " + digest.signature());
                throw new IllegalStateException("circuit breaker: " + digest.signature());
            }
            repairs++;
            Map<String, String> repair = new HashMap<>(firstCodeValues);
            repair.put("REPAIR_NOTE", "ERREUR À CORRIGER (signature " + digest.signature() + ")\n"
                    + String.join("\n", digest.messages()) + "\n" + digest.rawTail());
            JsonNode fixed = callStage("CODE", runId, repair, ctx, modelKey);
            generated = toFiles(fixed);
            result = checkClient.applyAndCheck(runId, generated);
        }
        repo.event(runId, "TESTS", "SUCCEEDED", "checks verts");

        String framework = "fake-exposer".equals(targetApp) ? "node:test" : "JUnit5";
        Map<String, String> testValues = new HashMap<>(base);
        testValues.put("FRAMEWORK", framework);
        testValues.put("SPECS_JSON", specs.toString());
        testValues.put("ARTIFACT_SUMMARY", generated.stream().map(Model.GeneratedFile::path)
                .collect(Collectors.joining(", ")));
        JsonNode tests = callStage("TESTS", runId, testValues, ctx, modelKey);
        List<Model.GeneratedFile> testFiles = toTestFiles(tests);
        generated.addAll(testFiles);
        repo.saveArtifacts(runId, "TESTS", "TEST_FILE", testFiles);
        result = checkClient.applyAndCheck(runId, generated);
        if (!result.ok()) {
            throw new IllegalStateException("tests générés en échec: " + result.signature());
        }

        Map<String, String> reviewValues = new HashMap<>(base);
        reviewValues.put("DIFF", generated.size() + " fichiers, "
                + generated.stream().mapToInt(f -> f.content().length()).sum() + " caractères");
        reviewValues.put("CHECKS", "OK (" + result.checkType() + ")");
        reviewValues.put("TRACEABILITY", "générée depuis " + reqIds.size() + " exigences");
        JsonNode review = callStage("REVIEW", runId, reviewValues, ctx, modelKey);
        repo.event(runId, "REVIEW", "SUCCEEDED", String.valueOf(review.path("decision").asText()));

        String branch = "factory/" + run.get("request_id") + "/" + runId;
        Model.PrRef pr = scmClient.createDraftPr(runId, branch, String.valueOf(run.get("title")),
                "Revue: " + review.path("summary").asText(), generated);
        audit.append(runId, "system", "DRAFT_PR_CREATED", pr.url(), Map.of("number", pr.number()));
        repo.event(runId, "GITLAB_MR", "AWAITING_HUMAN", pr.url());
        return pr;
    }

    private JsonNode callStage(String stage, UUID runId, Map<String, String> values,
                               DomainValidator.Context ctx, String modelKey) throws Exception {
        int max = props.limits().maxAttempts(stage);
        Map<String, String> current = new HashMap<>(values);
        for (int attempt = 1; attempt <= max; attempt++) {
            String prompt = prompts.render(stage, current);
            long t0 = System.currentTimeMillis();
            String raw = gateway.call(prompt);
            JsonNode output = mapper.readTree(stripFences(raw));
            List<String> errors = new ArrayList<>(schemaValidator.validate(stage, output));
            errors.addAll(domainValidator.validate(stage, output, ctx));
            repo.saveAgentOutput(runId, stage, attempt, output, errors, Hashes.sha256(prompt),
                    modelKey, 0, 0, System.currentTimeMillis() - t0);
            audit.append(runId, "system", "AGENT_OUTPUT_SAVED", stage + "/" + attempt,
                    Map.of("errors", errors.size()));
            if (errors.isEmpty()) return output;
            current.put("REPAIR_NOTE", "ERREURS DE VALIDATION À CORRIGER:\n"
                    + String.join("\n", errors));
        }
        throw new IllegalStateException(stage + ": échec après " + max + " tentatives");
    }

    private String ragContext(List<Model.Chunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (Model.Chunk c : chunks) {
            sb.append("--- source_uri: ").append(c.sourceUri()).append(" ---\n")
                    .append(c.content()).append("\n\n");
        }
        return sb.toString();
    }

    private String repoContext(String repoName, List<String> tree) {
        StringBuilder sb = new StringBuilder("ARBORESCENCE (partielle):\n");
        tree.stream().limit(80).forEach(p -> sb.append(p).append("\n"));
        sb.append("\nFICHIERS CANDIDATS:\n");
        tree.stream().filter(p -> p.contains("i18n") || p.contains("lang") || p.contains("score")
                        || p.contains("heuristics") || p.endsWith(".html"))
                .limit(6).forEach(p -> {
                    try {
                        String text = repoClient.read(repoName, p);
                        String[] lines = text.split("\\R", -1);
                        String head = String.join("\n", java.util.Arrays.copyOfRange(
                                lines, 0, Math.min(300, lines.length)));
                        sb.append("\n// FILE: ").append(p).append("\n").append(head).append("\n");
                    } catch (Exception ignored) {
                        // lecture best-effort : l'orchestrateur continue avec les autres fichiers
                    }
                });
        return sb.toString();
    }

    private List<Model.GeneratedFile> toFiles(JsonNode code) {
        List<Model.GeneratedFile> out = new ArrayList<>();
        for (JsonNode f : code.path("files")) {
            out.add(new Model.GeneratedFile(f.path("path").asText(), f.path("content").asText()));
        }
        return out;
    }

    private List<Model.GeneratedFile> toTestFiles(JsonNode tests) {
        List<Model.GeneratedFile> out = new ArrayList<>();
        for (JsonNode f : tests.path("testFiles")) {
            out.add(new Model.GeneratedFile(f.path("path").asText(), f.path("content").asText()));
        }
        return out;
    }

    private List<String> textArray(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) n.forEach(x -> out.add(x.asText()));
        return out;
    }

    private String stripFences(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("```\\s*$", "");
        }
        return s.trim();
    }
}
