package com.anantghadi.ragchatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Retrieval-Augmented Generation (RAG) document Q&amp;A API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RagChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagChatbotApplication.class, args);
    }
}
