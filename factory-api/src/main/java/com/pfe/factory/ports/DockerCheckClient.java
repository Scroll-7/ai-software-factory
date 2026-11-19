package com.pfe.factory.ports;

import com.pfe.factory.domain.Model;
import com.pfe.factory.validation.ErrorDigest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Vérification isolée : les fichiers générés sont écrits dans workspaces/checks/<runId>
 * puis compilés/testés dans un conteneur Docker SANS RÉSEAU (--network none).
 */
@Component
public class DockerCheckClient implements CheckClient {
    private final LocalRepo repos;

    public DockerCheckClient(LocalRepo repos) {
        this.repos = repos;
    }

    @Override
    public Model.CheckResult applyAndCheck(UUID runId, List<Model.GeneratedFile> files) {
        boolean java = files.stream().anyMatch(f -> f.path().endsWith(".java")
                || f.path().endsWith("pom.xml"));
        String image = java ? "maven:3.9-eclipse-temurin-21" : "node:20-alpine";
        String shellCommand = java ? "mvn -q test" : "node --test test/*.test.js";
        String checkType = "UNIT_TEST";

        Path dir = repos.workspaces().resolve("checks").resolve(runId.toString()).normalize();
        try {
            Files.createDirectories(dir);
            for (Model.GeneratedFile file : files) {
                Path target = safe(dir, file.path());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("préparation du check impossible", e);
        }

        String mount = dir.toAbsolutePath().toString().replace('\\', '/');
        String[] command = {"docker", "run", "--rm", "--network", "none",
                "-v", mount + ":/work", "-w", "/work", image, "sh", "-c", shellCommand};
        long started = System.currentTimeMillis();
        ExecResult result = exec(command);
        String signature = ErrorDigest.from(result.stdout(), result.stderr(),
                dir.toString(), checkType).signature();
        return new Model.CheckResult(result.exit() == 0, checkType,
                tail(result.stdout()), tail(result.stderr()), signature);
    }

    private Path safe(Path base, String path) {
        Path rel = Path.of(path.replace('\\', '/')).normalize();
        if (rel.isAbsolute() || rel.startsWith("..")) {
            throw new IllegalArgumentException("fichier généré hors périmètre: " + path);
        }
        Path target = base.resolve(rel).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("fichier généré hors périmètre: " + path);
        }
        return target;
    }

    private String tail(String text) {
        return text.length() > 4000 ? text.substring(text.length() - 4000) : text;
    }

    private ExecResult exec(String[] command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(false);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(300, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("check Docker trop long");
            }
            return new ExecResult(p.exitValue(), out, err);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("docker run impossible", e);
        }
    }

    private record ExecResult(int exit, String stdout, String stderr) {}
}
