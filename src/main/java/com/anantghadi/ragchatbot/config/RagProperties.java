package com.anantghadi.ragchatbot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration for storage, chunking and retrieval ({@code rag.*}).
 */
@Validated
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @Valid @NotNull @DefaultValue Storage storage,
        @Valid @NotNull @DefaultValue Chunking chunking,
        @Valid @NotNull @DefaultValue Retrieval retrieval) {

    /**
     * Where the local vector store and the document registry are persisted.
     */
    public record Storage(
            @NotNull @DefaultValue("data/vector-store.json") Path vectorStoreFile,
            @NotNull @DefaultValue("data/documents.json") Path registryFile) {
    }

    /**
     * Parameters handed to Spring AI's {@code TokenTextSplitter}.
     */
    public record Chunking(
            @Min(50) @Max(8000) @DefaultValue("400") int chunkSize,
            @Min(1) @DefaultValue("200") int minChunkSizeChars,
            @Min(1) @DefaultValue("5") int minChunkLengthToEmbed,
            @Min(1) @DefaultValue("10000") int maxNumChunks) {
    }

    /**
     * Similarity search defaults used when answering a question.
     */
    public record Retrieval(
            @Min(1) @Max(20) @DefaultValue("4") int topK,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.5") double similarityThreshold) {
    }
}
