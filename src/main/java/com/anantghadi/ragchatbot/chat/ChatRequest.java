package com.anantghadi.ragchatbot.chat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question to answer from the ingested documents.
 *
 * @param question the user's question
 * @param topK optional number of chunks to retrieve; falls back to {@code rag.retrieval.top-k}
 */
public record ChatRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must be at most 2000 characters")
        String question,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must be at most 20")
        Integer topK) {
}
