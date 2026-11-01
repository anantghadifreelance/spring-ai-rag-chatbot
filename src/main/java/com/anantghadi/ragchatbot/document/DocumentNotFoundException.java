package com.anantghadi.ragchatbot.document;

/**
 * Thrown when a document id is not present in the registry.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String documentId) {
        super("No document found with id '%s'".formatted(documentId));
    }
}
