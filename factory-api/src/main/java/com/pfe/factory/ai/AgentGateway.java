package com.pfe.factory.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
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
        var response = chatModel.call(new Prompt(prompt));
        return response.getResult().getOutput().getText();
    }
}
