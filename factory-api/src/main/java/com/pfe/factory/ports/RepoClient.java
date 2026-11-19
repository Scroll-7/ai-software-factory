package com.pfe.factory.ports;

import java.util.List;

public interface RepoClient {
    List<String> tree(String repo, String ref);
    String read(String repo, String path);

    /** Remet le workspace local sur la branche par défaut propre avant un nouveau run. */
    void resetToDefault(String repo);
}
