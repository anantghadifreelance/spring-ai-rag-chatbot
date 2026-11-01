package com.anantghadi.ragchatbot.document;

import com.anantghadi.ragchatbot.config.RagProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Keeps track of ingested documents (the vector store itself only knows about chunks) and
 * persists the catalogue to a small JSON file so it survives restarts.
 */
@Component
public class DocumentRegistry {

    private static final TypeReference<List<IngestedDocument>> LIST_TYPE = new TypeReference<>() { };

    private final Map<String, IngestedDocument> documents = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path file;

    public DocumentRegistry(ObjectMapper objectMapper, RagProperties properties) {
        this.objectMapper = objectMapper;
        this.file = properties.storage().registryFile();
        load();
    }

    public List<IngestedDocument> findAll() {
        return documents.values().stream()
                .sorted(Comparator.comparing(IngestedDocument::ingestedAt).reversed())
                .toList();
    }

    public Optional<IngestedDocument> findById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    public synchronized void save(IngestedDocument document) {
        documents.put(document.id(), document);
        persist(documents.values());
    }

    public synchronized void remove(String id) {
        if (documents.remove(id) != null) {
            persist(documents.values());
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            objectMapper.readValue(file.toFile(), LIST_TYPE).forEach(doc -> documents.put(doc.id(), doc));
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Could not read document registry " + file.toAbsolutePath(), ex);
        }
    }

    private void persist(Collection<IngestedDocument> snapshot) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), List.copyOf(snapshot));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Could not write document registry " + file.toAbsolutePath(), ex);
        }
    }
}
