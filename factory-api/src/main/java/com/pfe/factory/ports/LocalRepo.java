package com.pfe.factory.ports;

import com.pfe.factory.config.FactoryProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Accès local aux dépôts GitHub : clone superficiel dans un dossier gitignoré,
 * lecture et écriture avec protection contre le path traversal.
 * Aucun appel réseau autre que git clone/push vers GitHub.
 */
@Service
public class LocalRepo {
    private record Result(int exit, String stdout, String stderr) {}

    private final Path workspaces;

    public LocalRepo(FactoryProperties props) {
        String configured = props.workspaces() == null ? null : props.workspaces().dir();
        if (configured == null || configured.isBlank()) {
            configured = System.getenv().getOrDefault("PFE_WORKSPACES", "workspaces");
        }
        this.workspaces = Path.of(configured).toAbsolutePath().normalize();
    }

    public Path workspaces() {
        return workspaces;
    }

    public Path dir(String repo) {
        if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Nom de dépôt invalide: " + repo);
        }
        return workspaces.resolve(repo.substring(repo.lastIndexOf('/') + 1)).normalize();
    }

    public Path ensureClone(String repo) {
        Path dir = dir(repo);
        if (!Files.isDirectory(dir.resolve(".git"))) {
            try {
                Files.createDirectories(workspaces);
                Result r = run(workspaces, "git", "clone", "--depth", "1",
                        "https://github.com/" + repo + ".git", dir.toString());
                if (r.exit() != 0) {
                    throw new IllegalStateException("git clone échoué: " + r.stderr());
                }
            } catch (IOException e) {
                throw new IllegalStateException("clone local impossible", e);
            }
        }
        return dir;
    }

    public List<String> tree(String repo) {
        Result r = run(ensureClone(repo), "git", "ls-files");
        if (r.exit() != 0) {
            throw new IllegalStateException("git ls-files échoué: " + r.stderr());
        }
        List<String> out = new ArrayList<>();
        for (String line : r.stdout().split("\\R")) {
            if (!line.isBlank()) out.add(line);
        }
        return out;
    }

    public String read(String repo, String path) {
        try {
            return Files.readString(safe(ensureClone(repo), path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("lecture impossible: " + path, e);
        }
    }

    public void write(String repo, String path, String content) {
        try {
            Path target = safe(ensureClone(repo), path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("écriture impossible: " + path, e);
        }
    }

    public void git(String repo, String... args) {
        Path dir = ensureClone(repo);
        String[] cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        Result r = run(dir, cmd);
        if (r.exit() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " échoué: "
                    + r.stderr() + "\n" + r.stdout());
        }
    }

    /** exit 0 = aucune différence, exit 1 = des changements sont indexés. */
    public boolean hasStagedChanges(String repo) {
        Result r = run(ensureClone(repo), "git", "diff", "--cached", "--quiet");
        return r.exit() == 1;
    }

    public String defaultBranch(String repo) {
        Result r = run(ensureClone(repo), "git", "symbolic-ref", "--short",
                "refs/remotes/origin/HEAD");
        String value = r.stdout().trim();
        if (value.startsWith("origin/")) value = value.substring("origin/".length());
        return value.isEmpty() ? "main" : value;
    }

    private Path safe(Path base, String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("chemin vide");
        Path rel = Path.of(path.replace('\\', '/')).normalize();
        if (rel.isAbsolute() || rel.startsWith("..")) {
            throw new IllegalArgumentException("chemin refusé (hors dépôt): " + path);
        }
        Path target = base.resolve(rel).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("chemin refusé (hors dépôt): " + path);
        }
        return target;
    }

    private Result run(Path dir, String... command) {
        try {
            Path out = Files.createTempFile("factory-out-", ".txt");
            Path err = Files.createTempFile("factory-err-", ".txt");
            try {
                ProcessBuilder pb = new ProcessBuilder(command)
                        .directory(dir.toFile())
                        .redirectOutput(out.toFile())
                        .redirectError(err.toFile());
                Process p = pb.start();
                if (!p.waitFor(180, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    throw new IllegalStateException("commande trop lente: " + command[0]);
                }
                return new Result(p.exitValue(),
                        Files.readString(out, StandardCharsets.UTF_8),
                        Files.readString(err, StandardCharsets.UTF_8));
            } finally {
                Files.deleteIfExists(out);
                Files.deleteIfExists(err);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("exécution impossible: " + command[0], e);
        }
    }
}
