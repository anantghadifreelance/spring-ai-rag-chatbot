package com.anantghadi.ragchatbot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.anantghadi.ragchatbot.config.RagProperties;
import com.anantghadi.ragchatbot.config.VectorStorePersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

class DocumentIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:15:30Z");

    @TempDir
    Path tempDir;

    private VectorStore vectorStore;
    private VectorStorePersistence persistence;
    private DocumentRegistry registry;
    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        persistence = mock(VectorStorePersistence.class);
        RagProperties properties = new RagProperties(
                new RagProperties.Storage(tempDir.resolve("vector-store.json"), tempDir.resolve("documents.json")),
                new RagProperties.Chunking(60, 50, 5, 1000),
                new RagProperties.Retrieval(4, 0.5));
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        registry = new DocumentRegistry(objectMapper, properties);
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(60)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(1000)
                .withKeepSeparator(true)
                .build();
        service = new DocumentIngestionService(new DocumentParser(), splitter, vectorStore, persistence, registry,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void splitsTextIntoChunksWithTraceableMetadataAndStoresThem() {
        var file = new MockMultipartFile("file", "faq.md", "text/markdown", longText(40).getBytes(StandardCharsets.UTF_8));

        IngestedDocument result = service.ingest(file);

        List<Document> stored = capturedChunks();
        assertThat(stored).hasSizeGreaterThan(1).hasSize(result.chunkCount());
        assertThat(result.chunkIds()).containsExactlyElementsOf(stored.stream().map(Document::getId).toList());
        assertThat(result.filename()).isEqualTo("faq.md");
        assertThat(result.contentType()).isEqualTo("text/markdown");
        assertThat(result.ingestedAt()).isEqualTo(NOW);

        for (int i = 0; i < stored.size(); i++) {
            Document chunk = stored.get(i);
            assertThat(chunk.getText()).isNotBlank();
            assertThat(chunk.getMetadata())
                    .containsEntry(DocumentMetadata.DOCUMENT_ID, result.id())
                    .containsEntry(DocumentMetadata.SOURCE, "faq.md")
                    .containsEntry(DocumentMetadata.CHUNK_INDEX, i);
        }
        assertThat(registry.findById(result.id())).contains(result);
    }

    @Test
    void persistsVectorStoreAfterAddingChunks() {
        service.ingest(new MockMultipartFile("file", "notes.txt", "text/plain", longText(5).getBytes()));

        InOrder order = inOrder(vectorStore, persistence);
        order.verify(vectorStore).add(anyList());
        order.verify(persistence).flush();
    }

    @Test
    void chunkIdsAreUniqueUuids() {
        IngestedDocument result = service.ingest(
                new MockMultipartFile("file", "notes.txt", "text/plain", longText(40).getBytes()));

        assertThat(result.chunkIds()).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void stripsClientSuppliedPathsFromFilename() {
        IngestedDocument result = service.ingest(
                new MockMultipartFile("file", "../../etc/secret-notes.txt", "text/plain", longText(3).getBytes()));

        assertThat(result.filename()).isEqualTo("secret-notes.txt");
    }

    @Test
    void ingestsPdfPagesAndKeepsPageMetadata() throws IOException {
        byte[] pdf = pdfWithText("Spring AI makes Retrieval Augmented Generation simple for Java developers.");

        IngestedDocument result = service.ingest(new MockMultipartFile("file", "guide.pdf", "application/pdf", pdf));

        assertThat(result.contentType()).isEqualTo("application/pdf");
        List<Document> stored = capturedChunks();
        assertThat(stored).isNotEmpty();
        assertThat(stored.get(0).getText()).contains("Retrieval Augmented Generation");
        assertThat(stored.get(0).getMetadata())
                .containsEntry(DocumentMetadata.SOURCE, "guide.pdf")
                .containsKey("page_number");
    }

    @Test
    void rejectsUnsupportedFileTypes() {
        var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(UnsupportedDocumentTypeException.class)
                .hasMessageContaining("photo.png");
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void rejectsEmptyFiles() {
        var file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsFilesWithoutExtractableText() {
        var file = new MockMultipartFile("file", "blank.txt", "text/plain", "   \n\t  ".getBytes());

        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(DocumentProcessingException.class);
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void rejectsCorruptPdf() {
        var file = new MockMultipartFile("file", "broken.pdf", "application/pdf", "not a pdf".getBytes());

        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("broken.pdf");
    }

    @Test
    void deleteRemovesChunksFromVectorStoreAndRegistry() {
        IngestedDocument ingested = service.ingest(
                new MockMultipartFile("file", "notes.txt", "text/plain", longText(10).getBytes()));

        service.delete(ingested.id());

        verify(vectorStore).delete(ingested.chunkIds());
        assertThat(registry.findById(ingested.id())).isEmpty();
        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void deleteOfUnknownDocumentFails() {
        assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @SuppressWarnings("unchecked")
    private List<Document> capturedChunks() {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        return captor.getValue();
    }

    private static String longText(int paragraphs) {
        return IntStream.rangeClosed(1, paragraphs)
                .mapToObj(i -> "Paragraph " + i + ". Spring Boot auto-configuration creates beans when their "
                        + "conditions match, and starters bring curated dependencies for web, data and AI.")
                .collect(Collectors.joining("\n\n"));
    }

    private static byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
