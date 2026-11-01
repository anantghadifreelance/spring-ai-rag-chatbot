package com.anantghadi.ragchatbot.chat;

/**
 * A retrieved chunk that was handed to the model as grounding context.
 */
public record SourceReference(String documentId, String filename, String chunkId, Double score) {
}
