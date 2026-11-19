package com.pfe.factory.ports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.factory.domain.Model;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * SCM local : crée une branche, commite les fichiers générés et pousse vers GitHub.
 * La Draft PR est créée via l'API si PFE_GITHUB_TOKEN est défini ; sinon on renvoie
 * l'URL de comparaison pour que tu l'ouvres manuellement. Aucun secret n'est stocké.
 */
@Component
public class GitHubScmClient implements ScmClient {
    private final LocalRepo repos;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    public GitHubScmClient(LocalRepo repos, ObjectMapper mapper) {
        this.repos = repos;
        this.mapper = mapper;
    }

    @Override
    public Model.PrRef createDraftPr(UUID runId, String repo, String branch, String title,
                                     String body, List<Model.GeneratedFile> files) {
        repos.git(repo, "checkout", "-B", branch);
        for (Model.GeneratedFile file : files) {
            repos.write(repo, file.path(), file.content());
        }
        repos.git(repo, "add", "-A");
        if (!repos.hasStagedChanges(repo)) {
            throw new IllegalStateException("aucun changement à proposer pour " + branch);
        }
        repos.git(repo, "-c", "user.name=AI Software Factory",
                "-c", "user.email=factory@localhost", "commit", "-m", title);
        repos.git(repo, "push", "-u", "origin", branch);

        String token = System.getenv("PFE_GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            return createViaApi(repo, branch, title, body, token);
        }
        String encoded = URLEncoder.encode(branch, StandardCharsets.UTF_8);
        String url = "https://github.com/" + repo + "/compare/"
                + repos.defaultBranch(repo) + "..." + encoded;
        return new Model.PrRef(0, url);
    }

    private Model.PrRef createViaApi(String repo, String branch, String title,
                                     String body, String token) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("title", title);
            payload.put("head", branch);
            payload.put("base", repos.defaultBranch(repo));
            payload.put("body", body);
            payload.put("draft", true);

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.github.com/repos/" + repo + "/pulls"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GitHub PR HTTP " + response.statusCode()
                        + " : " + response.body());
            }
            var json = mapper.readTree(response.body());
            return new Model.PrRef(json.path("number").asInt(), json.path("html_url").asText());
        } catch (Exception e) {
            throw new IllegalStateException("création Draft PR impossible", e);
        }
    }
}
