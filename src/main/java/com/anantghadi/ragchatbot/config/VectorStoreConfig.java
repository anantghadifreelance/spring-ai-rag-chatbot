package com.anantghadi.ragchatbot.config;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires a file-backed {@link SimpleVectorStore} as the default {@link VectorStore}.
 *
 * <p>Set {@code rag.vector-store.type} to anything other than {@code simple} (for example {@code pgvector})
 * to switch this bean off and let a Spring AI vector store starter provide the {@link VectorStore} instead.
 * The rest of the application only depends on the {@code VectorStore} interface.
 */
@Configuration(proxyBeanMethods = false)
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "rag.vector-store", name = "type", havingValue = "simple", matchIfMissing = true)
    SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel, RagProperties properties) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = properties.storage().vectorStoreFile().toFile();
        if (file.isFile() && file.length() > 0) {
            store.load(file);
            log.info("Loaded vector store snapshot from {}", file.getAbsolutePath());
        }
        return store;
    }

    @Bean
    VectorStorePersistence vectorStorePersistence(VectorStore vectorStore, RagProperties properties) {
        if (vectorStore instanceof SimpleVectorStore simpleVectorStore) {
            Path target = properties.storage().vectorStoreFile();
            return () -> {
                createParentDirectories(target);
                simpleVectorStore.save(target.toFile());
            };
        }
        // Server-backed stores (pgvector, Redis, ...) persist on their own.
        return VectorStorePersistence.NO_OP;
    }

    private static void createParentDirectories(Path target) {
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Could not create directory " + parent, ex);
        }
    }
}
