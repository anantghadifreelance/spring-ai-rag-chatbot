package com.anantghadi.ragchatbot.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void returnsAnswerWithSources() throws Exception {
        when(chatService.ask(new ChatRequest("What is Spring Boot?", null))).thenReturn(new ChatAnswer(
                "An opinionated layer on top of Spring.", true,
                List.of(new SourceReference("doc-1", "faq.md", "chunk-a", 0.87))));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is Spring Boot?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("An opinionated layer on top of Spring."))
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.sources[0].filename").value("faq.md"))
                .andExpect(jsonPath("$.sources[0].chunkId").value("chunk-a"))
                .andExpect(jsonPath("$.sources[0].score").value(0.87));
    }

    @Test
    void blankQuestionIsValidationProblem() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.question").value("question must not be blank"));
        verifyNoInteractions(chatService);
    }

    @Test
    void outOfRangeTopKIsValidationProblem() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Hi\",\"topK\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.topK").value("topK must be at most 20"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void providerFailureIsBadGatewayProblem() throws Exception {
        when(chatService.ask(any())).thenThrow(new NonTransientAiException("401 - invalid api key"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is Spring Boot?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("AI provider error"))
                .andExpect(jsonPath("$.detail").value(
                        "The language model provider could not complete the request. Please retry later."));
    }
}
