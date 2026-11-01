package com.anantghadi.ragchatbot.document;

import java.time.Instant;
import java.util.List;

/**
 * API view of an ingested document.
 */
public record DocumentResponse(
        String id,
        String filename,
        String contentType,
        long sizeBytes,
        int chunkCount,
        List<String> chunkIds,
        Instant ingestedAt) {

    public static DocumentResponse from(IngestedDocument document) {
        return new DocumentResponse(document.id(), document.filename(), document.contentType(),
                document.sizeBytes(), document.chunkCount(), document.chunkIds(), document.ingestedAt());
    }
}
