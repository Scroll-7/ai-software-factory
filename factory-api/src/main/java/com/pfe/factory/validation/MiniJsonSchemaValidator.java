package com.pfe.factory.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mini-validateur couvrant UNIQUEMENT les mots-clés utilisés par nos schémas v1 :
 * type, const, enum, required, additionalProperties, properties, items,
 * min/maxItems, min/maxLength, pattern, minimum.
 * ASSUMPTION: suffisant pour le prototype. VERIFY si tu veux un validateur JSON Schema complet.
 */
@Component
public class MiniJsonSchemaValidator {
    private final Map<String, JsonNode> schemas = new HashMap<>();

    public MiniJsonSchemaValidator(ObjectMapper mapper) throws IOException {
        for (String s : List.of("specs", "design", "code", "tests", "review")) {
            try (var in = getClass().getResourceAsStream("/schemas/" + s + ".v1.json")) {
                if (in == null) throw new IOException("Schéma introuvable: " + s);
                schemas.put(s, mapper.readTree(in));
            }
        }
    }

    public List<String> validate(String stage, JsonNode node) {
        return validate(node, schemas.get(stage), "$");
    }

    private List<String> validate(JsonNode n, JsonNode s, String path) {
        List<String> errors = new ArrayList<>();
        if (s == null) return errors;

        if (s.has("type")) {
            List<String> expected = s.get("type").isArray()
                    ? toTextList(s.get("type")) : List.of(s.get("type").asText());
            if (!expected.contains(kind(n))) {
                errors.add(path + ": type attendu " + expected + " mais reçu " + kind(n));
            }
        }
        if (s.has("const") && !s.get("const").equals(n)) {
            errors.add(path + ": valeur attendue " + s.get("const"));
        }
        if (s.has("enum") && !toTextList(s.get("enum")).contains(n.asText())) {
            errors.add(path + ": valeur hors enum " + n.asText());
        }

        if (n.isObject()) {
            JsonNode props = s.path("properties");
            boolean denyExtra = s.path("additionalProperties").asBoolean(false);
            if (s.has("required")) {
                for (JsonNode r : s.get("required")) {
                    if (!n.has(r.asText())) errors.add(path + "." + r.asText() + ": champ manquant");
                }
            }
            var fields = n.fields();
            while (fields.hasNext()) {
                var e = fields.next();
                if (props.has(e.getKey())) {
                    errors.addAll(validate(e.getValue(), props.get(e.getKey()), path + "." + e.getKey()));
                } else if (denyExtra) {
                    errors.add(path + "." + e.getKey() + ": champ non autorisé");
                }
            }
        }

        if (n.isArray()) {
            if (s.has("minItems") && n.size() < s.get("minItems").asInt())
                errors.add(path + ": trop peu d'éléments");
            if (s.has("maxItems") && n.size() > s.get("maxItems").asInt())
                errors.add(path + ": trop d'éléments");
            if (s.has("items")) {
                for (int i = 0; i < n.size(); i++)
                    errors.addAll(validate(n.get(i), s.get("items"), path + "[" + i + "]"));
            }
        }

        if (n.isTextual()) {
            String v = n.asText();
            if (s.has("minLength") && v.length() < s.get("minLength").asInt())
                errors.add(path + ": texte trop court");
            if (s.has("maxLength") && v.length() > s.get("maxLength").asInt())
                errors.add(path + ": texte trop long");
            if (s.has("pattern") && !v.matches(s.get("pattern").asText()))
                errors.add(path + ": format invalide (" + s.get("pattern").asText() + ")");
        }

        if (n.isIntegralNumber() && s.has("minimum")
                && n.asInt() < s.get("minimum").asInt()) {
            errors.add(path + ": valeur trop petite");
        }
        return errors;
    }

    private String kind(JsonNode n) {
        if (n.isObject()) return "object";
        if (n.isArray()) return "array";
        if (n.isTextual()) return "string";
        if (n.isBoolean()) return "boolean";
        if (n.isIntegralNumber()) return "integer";
        if (n.isNumber()) return "number";
        if (n.isNull()) return "null";
        return "unknown";
    }

    private List<String> toTextList(JsonNode a) {
        List<String> out = new ArrayList<>();
        a.forEach(x -> out.add(x.asText()));
        return out;
    }
}
