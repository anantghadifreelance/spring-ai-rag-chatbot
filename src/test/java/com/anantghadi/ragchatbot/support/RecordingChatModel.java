package com.anantghadi.ragchatbot.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Offline {@link ChatModel} that records the last prompt and returns a canned answer.
 */
public class RecordingChatModel implements ChatModel {

    public static final String ANSWER = "Spring Boot is an opinionated layer on top of the Spring Framework.";

    private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt.set(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(ANSWER))));
    }

    public Prompt lastPrompt() {
        return lastPrompt.get();
    }

    public void reset() {
        lastPrompt.set(null);
    }
}
