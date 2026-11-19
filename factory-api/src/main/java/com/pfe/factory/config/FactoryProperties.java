package com.pfe.factory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "factory")
public record FactoryProperties(
        Prompts prompts,
        Limits limits,
        Rag rag,
        Github github,
        Scm scm,
        Workspaces workspaces,
        Telemetry telemetry) {

    public record Prompts(String version) {}

    public record Limits(
            Map<String, Integer> maxAttempts,
            int maxRepairsPerRun,
            int llmTimeoutSeconds) {
        public int maxAttempts(String stage) {
            return maxAttempts.getOrDefault(stage, 2);
        }
    }

    public record Rag(String baseUrl) {}

    public record Github(String webhookSecret) {}

    public record Scm(String defaultRepo, String fakeExposerRepo) {}

    public record Workspaces(String dir) {}

    public record Telemetry(boolean enabled) {}
}
