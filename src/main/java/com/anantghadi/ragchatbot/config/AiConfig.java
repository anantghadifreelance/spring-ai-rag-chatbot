package com.anantghadi.ragchatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI building blocks shared by the ingestion and the chat pipeline.
 */
@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    TokenTextSplitter tokenTextSplitter(RagProperties properties) {
        RagProperties.Chunking chunking = properties.chunking();
        return TokenTextSplitter.builder()
                .withChunkSize(chunking.chunkSize())
                .withMinChunkSizeChars(chunking.minChunkSizeChars())
                .withMinChunkLengthToEmbed(chunking.minChunkLengthToEmbed())
                .withMaxNumChunks(chunking.maxNumChunks())
                .withKeepSeparator(true)
                .build();
    }
}
