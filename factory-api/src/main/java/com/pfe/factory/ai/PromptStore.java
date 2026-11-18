package com.pfe.factory.ai;

import com.pfe.factory.config.FactoryProperties;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Charge common.fr.txt + <stage>.fr.txt + schemas/<stage>.v1.json et remplace les {{placeholders}}. */
@Component
public class PromptStore {
    private final FactoryProperties props;

    public PromptStore(FactoryProperties props) {
        this.props = props;
    }

    public String render(String stage, Map<String, String> values) throws IOException {
        String v = props.prompts().version();
        String agent = resource("/prompts/" + v + "/" + stage.toLowerCase() + ".fr.txt");
        String role = extractRole(agent);
        String body = stripRoleLine(agent);
        String schema = resource("/schemas/" + stage.toLowerCase() + ".v1.json");
        String common = resource("/prompts/" + v + "/common.fr.txt");

        Map<String, String> all = new HashMap<>(values);
        all.put("AGENT_ROLE", role);
        all.put("OUTPUT_SCHEMA", schema);
        all.put("REPAIR_NOTE", values.getOrDefault("REPAIR_NOTE", ""));

        String rendered = common + "\n\n" + body;
        for (var e : all.entrySet()) {
            rendered = rendered.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return rendered;
    }

    private String extractRole(String agent) {
        return agent.lines()
                .filter(l -> l.startsWith("AGENT_ROLE="))
                .map(l -> l.substring("AGENT_ROLE=".length()).trim())
                .findFirst()
                .orElse("un agent local");
    }

    private String stripRoleLine(String agent) {
        return agent.lines()
                .filter(l -> !l.startsWith("AGENT_ROLE="))
                .reduce("", (a, b) -> a + b + "\n");
    }

    private String resource(String path) throws IOException {
        try (var in = getClass().getResourceAsStream(path)) {
            if (in == null) throw new IOException("Ressource introuvable: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
