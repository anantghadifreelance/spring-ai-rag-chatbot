package com.anantghadi.ragchatbot.document;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * File types the ingestion pipeline understands, resolved from the file extension.
 */
public enum DocumentType {

    TEXT("text/plain", Set.of("txt")),
    MARKDOWN("text/markdown", Set.of("md", "markdown")),
    PDF("application/pdf", Set.of("pdf"));

    private final String mediaType;
    private final Set<String> extensions;

    DocumentType(String mediaType, Set<String> extensions) {
        this.mediaType = mediaType;
        this.extensions = extensions;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<DocumentType> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.extensions.contains(extension))
                .findFirst();
    }
}
