package com.anantghadi.ragchatbot.document;

import java.time.Instant;
import java.util.List;

/**
 * Registry entry for an uploaded document and the ids of the chunks it was split into.
 */
public record IngestedDocument(
        String id,
        String filename,
        String contentType,
        long sizeBytes,
        int chunkCount,
        List<String> chunkIds,
        Instant ingestedAt) {

    public IngestedDocument {
        chunkIds = List.copyOf(chunkIds);
    }
}
