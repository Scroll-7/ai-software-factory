package com.pfe.factory.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Règles métier qui ne peuvent pas être exprimées dans le schéma JSON seul. */
@Component
public class DomainValidator {

    public record Context(
            Set<String> allowedSourceUris,
            Set<String> knownPaths,
            Set<String> newPaths,
            Set<String> requirementIds,
            String targetApp) {}

    private static final Pattern FORBIDDEN = Pattern.compile(
            "(groq|gemini|openai|anthropic|https?://(?!localhost|127\\.0\\.0\\.1)|"
            + "fetch\\s*\\(|axios|XMLHttpRequest|requests\\.|urllib|HttpClient|WebClient|"
            + "ProcessBuilder|Runtime\\.getRuntime|child_process|\\bexec\\s*\\(|\\beval\\s*\\(|"
            + "innerHTML|dangerouslySetInnerHTML|pickle\\.loads|subprocess|API_KEY|"
            + "\\brequire\\s*\\()",
            Pattern.CASE_INSENSITIVE);

    // On ne bloque qu'une DEMANDE de données réelles / de production, pas une question méta
    // qui cite la règle de sécurité ("comment refuser les données réelles ?").
    private static final Pattern BLOCKING_WORDS = Pattern.compile(
            "confidentiel|cv réel|données? réelles? (?:à|de|sur|contenir|utiliser|fournir|requises?)"
                    + "|en production|appel(?:s)? (?:réseau )?externe(?:s)? (?:requis|nécessaire|obligatoire)",
            Pattern.CASE_INSENSITIVE);

    public List<String> validate(String stage, JsonNode o, Context c) {
        List<String> errors = new ArrayList<>();
        if (o == null || !o.isObject()) return List.of("sortie: objet JSON attendu");

        switch (stage.toLowerCase(Locale.ROOT)) {
            case "specs" -> {
                for (String uri : textArray(o.path("citations"))) {
                    if (!c.allowedSourceUris().contains(uri)) errors.add("citation inconnue: " + uri);
                }
                for (JsonNode r : o.path("requirements")) {
                    if (r.path("sourceUris").isEmpty()) errors.add(r.path("id").asText() + ": source_uri manquant");
                    for (String uri : textArray(r.path("sourceUris"))) {
                        if (!c.allowedSourceUris().contains(uri))
                            errors.add(r.path("id").asText() + ": source_uri inconnu " + uri);
                    }
                }
                for (String q : textArray(o.path("openQuestions"))) {
                    if (BLOCKING_WORDS.matcher(q).find()) errors.add("QUESTION_BLOQUANTE: " + q);
                }
            }
            case "design" -> {
                Set<String> covered = new HashSet<>();
                for (JsonNode f : o.path("filePlan")) {
                    String path = f.path("path").asText();
                    String action = f.path("action").asText();
                    if ("MODIFY".equals(action) && !c.knownPaths().contains(path))
                        errors.add("filePlan: fichier inexistant " + path);
                    if ("CREATE".equals(action) && c.knownPaths().contains(path))
                        errors.add("filePlan: CREATE sur un fichier existant " + path);
                    for (String r : textArray(f.path("requirementIds"))) covered.add(r);
                }
                for (String r : c.requirementIds()) {
                    if (!covered.contains(r)) errors.add("exigence non couverte par le plan: " + r);
                }
                for (String a : textArray(o.path("assumptions"))) {
                    if (a.startsWith("DEPENDANCE_A_VALIDER"))
                        errors.add("DEPENDANCE_A_VALIDER: validation humaine requise");
                }
            }
            case "code" -> {
                for (JsonNode f : o.path("files")) {
                    String path = f.path("path").asText();
                    if (!c.knownPaths().contains(path) && !c.newPaths().contains(path))
                        errors.add("code: chemin non autorisé " + path);
                    String content = f.path("content").asText();
                    if (content.trim().length() < 20) errors.add(path + ": contenu trop court / vide");
                    int lines = content.split("\\R", -1).length;
                    if (lines > 200) errors.add(path + ": fichier trop long (" + lines + " lignes)");
                    if (FORBIDDEN.matcher(content).find())
                        errors.add(path + ": appel externe ou API dangereuse détectée");
                    if (content.contains("TODO") || content.contains("FIXME"))
                        errors.add(path + ": marqueur TODO/FIXME interdit");
                    for (String r : textArray(f.path("requirementIds"))) {
                        if (!c.requirementIds().contains(r)) errors.add(path + ": exigence inconnue " + r);
                    }
                }
                JsonNode self = o.path("selfCheck");
                // ASSUMPTION : un fichier de données (JSON i18n) n'utilise pas de framework.
                // Le contrôle de dépendance est déjà couvert par FORBIDDEN + le check Docker.
                for (String key : List.of("noExternalCalls", "noSecrets", "pathsExist", "noUnsafeApi")) {
                    if (!self.path(key).asBoolean(false)) errors.add("selfCheck." + key + " = false");
                }
            }
            case "tests" -> {
                String framework = o.path("framework").asText();
                for (JsonNode f : o.path("testFiles")) {
                    String path = f.path("path").asText();
                    boolean ok = "node:test".equals(framework)
                            ? path.startsWith("test/") && path.endsWith(".test.js")
                            : path.startsWith("src/test/java/") && path.endsWith(".java");
                    if (!ok) errors.add("tests: chemin hors framework " + path);
                    String content = f.path("content").asText();
                    if (content.matches("(?s).*(Date\\.now|Math\\.random|new Random|http).*"))
                        errors.add(path + ": test non déterministe");
                    for (String r : textArray(f.path("requirementIds"))) {
                        if (!c.requirementIds().contains(r)) errors.add(path + ": exigence inconnue " + r);
                    }
                }
            }
            case "review" -> {
                if (o.path("externalCalls").size() > 0) errors.add("externalCalls doit être vide");
                boolean blocker = false;
                for (JsonNode f : o.path("findings")) {
                    if ("BLOCKER".equals(f.path("severity").asText())) blocker = true;
                }
                if (blocker && "APPROVE".equals(o.path("decision").asText()))
                    errors.add("decision APPROVE impossible avec un BLOCKER");
                JsonNode bias = o.path("biasChecklist");
                if ("demo-hr-app".equals(c.targetApp())) {
                    if (!bias.isObject()) errors.add("biasChecklist obligatoire pour demo-hr-app");
                    else for (String k : List.of("scoreNotDecision", "explanationProvided",
                            "noProtectedAttributes", "humanReviewStated")) {
                        if (!bias.path(k).asBoolean(false)) errors.add("biasChecklist." + k + " = false");
                    }
                } else if (!bias.isNull()) {
                    errors.add("biasChecklist doit être null pour fake-exposer");
                }
                Set<String> covered = new HashSet<>();
                for (JsonNode t : o.path("traceability")) {
                    if (t.path("covered").asBoolean(false)) covered.add(t.path("requirementId").asText());
                }
                for (String r : c.requirementIds()) {
                    if (!covered.contains(r)) errors.add("revue: exigence non couverte " + r);
                }
            }
            default -> errors.add("stage inconnu: " + stage);
        }
        return errors;
    }

    private List<String> textArray(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) n.forEach(x -> out.add(x.asText()));
        return out;
    }
}
