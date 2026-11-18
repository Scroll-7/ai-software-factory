package com.pfe.factory.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Petits types partagés entre l'orchestrateur et les ports (Lot 2 à 4). */
public final class Model {
    private Model() {}

    public record Chunk(String sourceUri, String content, JsonNode metadata) {}

    public record CheckResult(
            boolean ok,
            String checkType,
            String stdout,
            String stderr,
            String signature) {}

    public record PrRef(int number, String url) {}

    public record GeneratedFile(String path, String content) {}

    public record RepoContext(List<String> tree, List<FileSnippet> files, List<String> commands) {
        public record FileSnippet(String path, String content, int lines) {}
    }
}
