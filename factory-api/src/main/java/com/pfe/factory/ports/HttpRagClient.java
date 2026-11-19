package com.pfe.factory.ports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.factory.config.FactoryProperties;
import com.pfe.factory.domain.Model;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Appel réel au service RAG local — remplace le bouchon synthetic://stub. */
@Component
public class HttpRagClient implements RagClient {
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    public HttpRagClient(FactoryProperties props, ObjectMapper mapper) {
        this.baseUrl = props.rag().baseUrl().replaceAll("/+$", "");
        this.mapper = mapper;
    }

    @Override
    public List<Model.Chunk> search(String query, String kind, int k) {
        try {
            String body = mapper.writeValueAsString(Map.of("query", query, "kind", kind, "k", k));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/search"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(java.time.Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("RAG /search HTTP " + response.statusCode()
                        + " : " + response.body());
            }
            JsonNode results = mapper.readTree(response.body()).path("results");
            List<Model.Chunk> chunks = new ArrayList<>();
            for (JsonNode item : results) {
                chunks.add(new Model.Chunk(
                        item.path("sourceUri").asText(),
                        item.path("content").asText(""),
                        item.path("metadata")));
            }
            return chunks;
        } catch (Exception e) {
            throw new IllegalStateException("RAG local indisponible", e);
        }
    }
}
