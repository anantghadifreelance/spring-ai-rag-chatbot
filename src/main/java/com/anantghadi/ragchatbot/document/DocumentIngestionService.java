package com.anantghadi.ragchatbot.document;

import com.anantghadi.ragchatbot.config.VectorStorePersistence;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ingestion pipeline: <em>read &rarr; split &rarr; embed &rarr; store</em>.
 *
 * <p>Embedding happens inside {@link VectorStore#add(List)}, which calls the configured
 * {@code EmbeddingModel} for every chunk before indexing it.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentParser parser;
    private final TextSplitter textSplitter;
    private final VectorStore vectorStore;
    private final VectorStorePersistence persistence;
    private final DocumentRegistry registry;
    private final Clock clock;

    public DocumentIngestionService(DocumentParser parser, TextSplitter textSplitter, VectorStore vectorStore,
            VectorStorePersistence persistence, DocumentRegistry registry, Clock clock) {
        this.parser = parser;
        this.textSplitter = textSplitter;
        this.vectorStore = vectorStore;
        this.persistence = persistence;
        this.registry = registry;
        this.clock = clock;
    }

    public IngestedDocument ingest(MultipartFile file) {
        String filename = sanitizeFilename(file.getOriginalFilename());
        DocumentType type = DocumentType.fromFilename(filename)
                .orElseThrow(() -> new UnsupportedDocumentTypeException(filename));
        if (file.isEmpty()) {
            throw new InvalidDocumentException("Uploaded file '%s' is empty".formatted(filename));
        }
        return ingest(readBytes(file), filename, type);
    }

    public IngestedDocument ingest(byte[] content, String filename, DocumentType type) {
        String documentId = UUID.randomUUID().toString();
        Map<String, Object> baseMetadata = Map.of(
                DocumentMetadata.DOCUMENT_ID, documentId,
                DocumentMetadata.SOURCE, filename,
                DocumentMetadata.CONTENT_TYPE, type.mediaType());

        List<Document> parsed = parser.parse(content, filename, type, baseMetadata);
        List<Document> chunks = assignChunkIds(documentId, textSplitter.apply(parsed));
        if (chunks.isEmpty()) {
            throw new DocumentProcessingException("No extractable text found in '%s'".formatted(filename));
        }

        synchronized (this) {
            vectorStore.add(chunks);
            persistence.flush();
            IngestedDocument ingested = new IngestedDocument(documentId, filename, type.mediaType(),
                    content.length, chunks.size(), chunks.stream().map(Document::getId).toList(),
                    Instant.now(clock));
            registry.save(ingested);
            log.info("Ingested '{}' as {} chunk(s) [documentId={}]", filename, chunks.size(), documentId);
            return ingested;
        }
    }

    public List<IngestedDocument> findAll() {
        return registry.findAll();
    }

    public IngestedDocument findById(String documentId) {
        return registry.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    public synchronized void delete(String documentId) {
        IngestedDocument document = findById(documentId);
        if (!document.chunkIds().isEmpty()) {
            vectorStore.delete(document.chunkIds());
            persistence.flush();
        }
        registry.remove(documentId);
        log.info("Deleted '{}' and its {} chunk(s) [documentId={}]", document.filename(), document.chunkCount(),
                documentId);
    }

    /**
     * Gives every chunk a deterministic UUID derived from its document id and position, so chunk ids are
     * stable, traceable and valid for UUID-keyed stores such as pgvector.
     */
    private static List<Document> assignChunkIds(String documentId, List<Document> chunks) {
        List<Document> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Document chunk = chunks.get(index);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put(DocumentMetadata.CHUNK_INDEX, index);
            String chunkId = UUID.nameUUIDFromBytes((documentId + ":" + index).getBytes(StandardCharsets.UTF_8))
                    .toString();
            result.add(new Document(chunkId, chunk.getText(), metadata));
        }
        return result;
    }

    private static String sanitizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new InvalidDocumentException("A file name is required");
        }
        // Strip any client-supplied path (e.g. "C:\\docs\\faq.md" or "../faq.md").
        Path name = Path.of(originalFilename.replace('\\', '/')).getFileName();
        String cleaned = name == null ? "" : name.toString().strip();
        if (!StringUtils.hasText(cleaned)) {
            throw new InvalidDocumentException("A file name is required");
        }
        return cleaned;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Could not read uploaded file", ex);
        }
    }
}
