package com.anantghadi.ragchatbot.document;

/**
 * Metadata keys stamped on every chunk stored in the vector store.
 */
public final class DocumentMetadata {

    public static final String DOCUMENT_ID = "document_id";
    public static final String SOURCE = "source";
    public static final String CONTENT_TYPE = "content_type";
    public static final String CHUNK_INDEX = "chunk_index";

    private DocumentMetadata() {
    }
}
