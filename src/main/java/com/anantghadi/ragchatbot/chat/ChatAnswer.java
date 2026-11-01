package com.anantghadi.ragchatbot.chat;

import java.util.List;

/**
 * The model's answer plus the chunks it was grounded on.
 *
 * @param answer the generated (or fallback) answer
 * @param grounded {@code true} when at least one relevant chunk was retrieved and sent to the model
 * @param sources the retrieved chunks, most similar first
 */
public record ChatAnswer(String answer, boolean grounded, List<SourceReference> sources) {

    public ChatAnswer {
        sources = List.copyOf(sources);
    }
}
