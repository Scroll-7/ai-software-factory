package com.pfe.factory.workflow;

import com.pfe.factory.domain.Model;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** État en mémoire des exécutions asynchrones (prototype local, pas de télémétrie). */
@Component
public class ExecutionRegistry {
    public record State(String status, Integer prNumber, String prUrl, String error) {}

    private final ConcurrentMap<UUID, State> states = new ConcurrentHashMap<>();

    public void start(UUID runId) {
        states.put(runId, new State("RUNNING", null, null, null));
    }

    public void done(UUID runId, Model.PrRef pr) {
        states.put(runId, new State("AWAITING_REVIEW", pr.number(), pr.url(), null));
    }

    public void fail(UUID runId, Throwable error) {
        String message = error == null ? "erreur inconnue" : String.valueOf(error.getMessage());
        states.put(runId, new State("FAILED", null, null,
                message.length() > 500 ? message.substring(0, 500) : message));
    }

    public State get(UUID runId) {
        return states.getOrDefault(runId, new State("UNKNOWN", null, null, null));
    }
}
