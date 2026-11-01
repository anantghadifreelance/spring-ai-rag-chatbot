package com.anantghadi.ragchatbot.document;

/**
 * Thrown when an upload is structurally invalid (missing name, empty body, ...).
 */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
