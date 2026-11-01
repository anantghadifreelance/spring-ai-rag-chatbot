package com.anantghadi.ragchatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anantghadi.ragchatbot.chat.ChatService;
import com.anantghadi.ragchatbot.support.OfflineAiTestConfiguration;
import com.anantghadi.ragchatbot.support.RecordingChatModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end ingest &rarr; retrieve &rarr; answer &rarr; delete flow with the real {@code SimpleVectorStore},
 * real token splitting and offline fake models. Starts without {@code OPENAI_API_KEY}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OfflineAiTestConfiguration.class)
class RagChatbotApplicationIntegrationTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("rag.storage.vector-store-file", () -> dataDir.resolve("vector-store.json").toString());
        registry.add("rag.storage.registry-file", () -> dataDir.resolve("documents.json").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingChatModel chatModel;

    @BeforeEach
    void resetModel() {
        chatModel.reset();
    }

    @Test
    void ingestsSampleDocumentAnswersGroundedQuestionsAndForgetsItOnDelete() throws Exception {
        byte[] faq = Files.readAllBytes(Path.of("samples/spring-boot-interview-faq.md"));

        String body = mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "spring-boot-interview-faq.md", "text/markdown", faq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("spring-boot-interview-faq.md"))
                .andReturn().getResponse().getContentAsString();
        JsonNode document = objectMapper.readTree(body);
        String documentId = document.get("id").asText();
        assertThat(document.get("chunkCount").asInt()).isGreaterThan(1);
        assertThat(dataDir.resolve("vector-store.json")).isNotEmptyFile();
        assertThat(dataDir.resolve("documents.json")).isNotEmptyFile();

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documentId));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How does Spring Boot auto-configuration work?\",\"topK\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(RecordingChatModel.ANSWER))
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0].documentId").value(documentId))
                .andExpect(jsonPath("$.sources[0].filename").value("spring-boot-interview-faq.md"));

        String systemPrompt = chatModel.lastPrompt().getInstructions().stream()
                .filter(message -> message.getMessageType() == MessageType.SYSTEM)
                .findFirst().orElseThrow().getText();
        assertThat(systemPrompt).contains("[source: spring-boot-interview-faq.md").contains("ConditionalOnClass");

        mockMvc.perform(delete("/api/documents/{id}", documentId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/documents")).andExpect(jsonPath("$.length()").value(0));

        chatModel.reset();
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How does Spring Boot auto-configuration work?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(ChatService.NO_CONTEXT_ANSWER))
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.sources.length()").value(0));
        assertThat(chatModel.lastPrompt()).isNull();
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
