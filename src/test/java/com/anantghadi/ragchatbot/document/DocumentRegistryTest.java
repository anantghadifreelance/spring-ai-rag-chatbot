package com.anantghadi.ragchatbot.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.anantghadi.ragchatbot.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentRegistryTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @TempDir
    Path tempDir;

    @Test
    void survivesRestartByReloadingFromJsonFile() {
        RagProperties properties = properties();
        var first = new IngestedDocument("doc-1", "a.md", "text/markdown", 120, 2, List.of("c1", "c2"),
                Instant.parse("2026-11-01T10:00:00Z"));
        var second = new IngestedDocument("doc-2", "b.txt", "text/plain", 80, 1, List.of("c3"),
                Instant.parse("2026-11-01T11:00:00Z"));

        DocumentRegistry registry = new DocumentRegistry(objectMapper, properties);
        registry.save(first);
        registry.save(second);

        DocumentRegistry reloaded = new DocumentRegistry(objectMapper, properties);
        assertThat(reloaded.findAll()).containsExactly(second, first); // newest first
    }

    @Test
    void removeDeletesEntryPersistently() {
        RagProperties properties = properties();
        DocumentRegistry registry = new DocumentRegistry(objectMapper, properties);
        registry.save(new IngestedDocument("doc-1", "a.md", "text/markdown", 10, 1, List.of("c1"), Instant.now()));

        registry.remove("doc-1");

        assertThat(new DocumentRegistry(objectMapper, properties).findAll()).isEmpty();
    }

    private RagProperties properties() {
        return new RagProperties(
                new RagProperties.Storage(tempDir.resolve("store.json"), tempDir.resolve("nested/documents.json")),
                new RagProperties.Chunking(400, 200, 5, 10000),
                new RagProperties.Retrieval(4, 0.5));
    }
}
