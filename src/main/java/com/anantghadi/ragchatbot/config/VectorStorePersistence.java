package com.anantghadi.ragchatbot.config;

/**
 * Flushes the vector store to durable storage after it has been modified.
 *
 * <p>Only in-memory stores need this; server-backed stores use {@link #NO_OP}.
 */
@FunctionalInterface
public interface VectorStorePersistence {

    VectorStorePersistence NO_OP = () -> { };

    void flush();
}
