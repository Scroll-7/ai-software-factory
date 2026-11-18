package com.pfe.factory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.factory.domain.Model;
import com.pfe.factory.ports.CheckClient;
import com.pfe.factory.ports.RagClient;
import com.pfe.factory.ports.RepoClient;
import com.pfe.factory.ports.ScmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/**
 * Bouchons volontaires : chaque port sera implémenté dans un lot ultérieur.
 * Leur but est de laisser l'orchestrateur compiler sans faire croire que ça marche.
 */
@Configuration
public class StubPortsConfig {

    @Bean
    RagClient ragClient(ObjectMapper mapper) {
        return (query, kind, k) -> List.of(new Model.Chunk(
                "synthetic://stub",
                "CONTEXTE RAG NON ENCORE BRANCHÉ — Lot 2 à implémenter.",
                mapper.createObjectNode()));
    }

    @Bean
    RepoClient repoClient() {
        return new RepoClient() {
            @Override public List<String> tree(String repo, String ref) {
                throw new UnsupportedOperationException("RepoClient: Lot 3 à implémenter");
            }
            @Override public String read(String repo, String path) {
                throw new UnsupportedOperationException("RepoClient: Lot 3 à implémenter");
            }
        };
    }

    @Bean
    CheckClient checkClient() {
        return (runId, files) -> {
            throw new UnsupportedOperationException("CheckClient (Docker --network none): Lot 3 à implémenter");
        };
    }

    @Bean
    ScmClient scmClient() {
        return (runId, branch, title, body, files) -> {
            throw new UnsupportedOperationException("ScmClient GitHub: Lot 4 à implémenter");
        };
    }
}
