package com.anantghadi.ragchatbot.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Replaces the OpenAI models with offline fakes so the full application context starts without an API key
 * (the {@code test} profile disables the OpenAI chat and embedding auto-configuration).
 */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineAiTestConfiguration {

    @Bean
    HashingEmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }

    @Bean
    RecordingChatModel chatModel() {
        return new RecordingChatModel();
    }
}
