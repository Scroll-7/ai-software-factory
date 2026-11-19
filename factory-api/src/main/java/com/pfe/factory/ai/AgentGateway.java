package com.pfe.factory.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

/**
 * Appel unique vers le modèle LOCAL via Spring AI.
 * VERIFY: noms exacts de ChatModel / Prompt / getResult() pour la version Spring AI installée.
 * Aucune URL externe ne doit apparaître ici : base-url = http://localhost:11434 uniquement.
 */
@Service
public class AgentGateway {
    private final ChatModel chatModel;

    public AgentGateway(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String call(String prompt) {
        return call(prompt, null);
    }

    /**
     * Le modèle peut être surchargé par run (harnais d'évaluation 7B / 3B).
     * Les options anti-répétition restent les mêmes que dans application.yml.
     */
    public String call(String prompt, String modelName) {
        Prompt request = modelName == null || modelName.isBlank()
                ? new Prompt(prompt)
                : new Prompt(prompt, OllamaOptions.builder()
                        .model(modelName)
                        .temperature(0.1)
                        .repeatPenalty(1.15)
                        .repeatLastN(64)
                        .build());
        var response = chatModel.call(request);
        return response.getResult().getOutput().getText();
    }
}
