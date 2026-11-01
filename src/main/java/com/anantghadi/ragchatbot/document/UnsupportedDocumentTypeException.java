package com.anantghadi.ragchatbot.document;

/**
 * Thrown when an upload has an extension the ingestion pipeline cannot read.
 */
public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String filename) {
        super("Unsupported file type for '%s'. Allowed types: .txt, .md, .markdown, .pdf".formatted(filename));
    }
}
