package com.pfe.factory.ports;

import org.springframework.stereotype.Component;

import java.util.List;

/** Implémentation réelle de RepoClient (Lot 3). */
@Component
public class LocalRepoClient implements RepoClient {
    private final LocalRepo repos;

    public LocalRepoClient(LocalRepo repos) {
        this.repos = repos;
    }

    @Override
    public List<String> tree(String repo, String ref) {
        return repos.tree(repo);
    }

    @Override
    public String read(String repo, String path) {
        return repos.read(repo, path);
    }

    @Override
    public void resetToDefault(String repo) {
        repos.resetToDefault(repo);
    }
}
