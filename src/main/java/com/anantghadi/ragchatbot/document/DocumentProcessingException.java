package com.anantghadi.ragchatbot.document;

/**
 * Thrown when a supported file cannot be parsed or yields no text to index.
 */
public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException(String message) {
        super(message);
    }

    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
