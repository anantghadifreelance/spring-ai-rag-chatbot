package com.anantghadi.ragchatbot.document;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Turns raw uploaded bytes into Spring AI {@link Document}s (one per PDF page, one per text file).
 */
@Component
public class DocumentParser {

    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[ \\t\\x0B\\f]+");

    public List<Document> parse(byte[] content, String filename, DocumentType type, Map<String, Object> metadata) {
        try {
            List<Document> documents = switch (type) {
                case TEXT, MARKDOWN -> parseText(content, metadata);
                case PDF -> parsePdf(content, filename, metadata);
            };
            return documents.stream().filter(doc -> StringUtils.hasText(doc.getText())).toList();
        }
        catch (RuntimeException ex) {
            throw new DocumentProcessingException("Could not read '%s': %s".formatted(filename, ex.getMessage()), ex);
        }
    }

    private List<Document> parseText(byte[] content, Map<String, Object> metadata) {
        String text = new String(content, StandardCharsets.UTF_8).strip();
        return List.of(new Document(text, metadata));
    }

    private List<Document> parsePdf(byte[] content, String filename, Map<String, Object> metadata) {
        var reader = new PagePdfDocumentReader(new NamedByteArrayResource(content, filename));
        return reader.get().stream()
                .map(page -> {
                    Map<String, Object> merged = new HashMap<>(page.getMetadata());
                    merged.putAll(metadata);
                    return new Document(normalizeWhitespace(page.getText()), merged);
                })
                .toList();
    }

    /**
     * PDF layout extraction pads words with spaces to mimic columns; collapse those runs so chunks embed
     * cleanly while keeping line breaks.
     */
    private static String normalizeWhitespace(String text) {
        return text == null ? "" : HORIZONTAL_WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    /**
     * {@link ByteArrayResource} that reports a filename, which the PDF reader copies into chunk metadata.
     */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
