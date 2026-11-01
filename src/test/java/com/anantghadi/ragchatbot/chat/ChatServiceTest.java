package com.anantghadi.ragchatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.anantghadi.ragchatbot.config.RagProperties;
import com.anantghadi.ragchatbot.document.DocumentMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

class ChatServiceTest {

    private ChatModel chatModel;
    private VectorStore vectorStore;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        vectorStore = mock(VectorStore.class);
        RagProperties properties = new RagProperties(
                new RagProperties.Storage(Path.of("unused.json"), Path.of("unused-registry.json")),
                new RagProperties.Chunking(400, 200, 5, 10000),
                new RagProperties.Retrieval(4, 0.6));
        chatService = new ChatService(ChatClient.builder(chatModel).build(), vectorStore, properties,
                new ClassPathResource("prompts/rag-system.st"));
    }

    @Test
    void answersFromRetrievedContextAndReturnsSources() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunk("chunk-1", "doc-1", "faq.md", "Starters are curated dependency descriptors.", 0.91),
                chunk("chunk-2", "doc-2", "guide.pdf", "Actuator exposes health endpoints.", 0.72)));
        when(chatModel.call(any(Prompt.class))).thenReturn(response("Starters bundle compatible dependencies."));

        ChatAnswer answer = chatService.ask(new ChatRequest("  What are starters?  ", null));

        assertThat(answer.answer()).isEqualTo("Starters bundle compatible dependencies.");
        assertThat(answer.grounded()).isTrue();
        assertThat(answer.sources()).containsExactly(
                new SourceReference("doc-1", "faq.md", "chunk-1", 0.91),
                new SourceReference("doc-2", "guide.pdf", "chunk-2", 0.72));
    }

    @Test
    void sendsGroundedSystemPromptWithContextAndUserQuestion() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunk("chunk-1", "doc-1", "faq.md", "Constructor injection is the recommended style.", 0.88)));
        when(chatModel.call(any(Prompt.class))).thenReturn(response("Use constructor injection."));

        chatService.ask(new ChatRequest("Which injection style is recommended?", null));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();

        String system = prompt.getInstructions().stream()
                .filter(message -> message.getMessageType() == MessageType.SYSTEM)
                .findFirst().orElseThrow().getText();
        assertThat(system)
                .contains("ONLY the information in the")
                .contains("I don't know based on the provided documents.")
                .contains("[source: faq.md | chunk: chunk-1]")
                .contains("Constructor injection is the recommended style.");
        assertThat(prompt.getUserMessage().getText()).isEqualTo("Which injection style is recommended?");
    }

    @Test
    void usesConfiguredTopKAndSimilarityThresholdByDefault() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        chatService.ask(new ChatRequest("anything", null));

        SearchRequest request = capturedSearch();
        assertThat(request.getQuery()).isEqualTo("anything");
        assertThat(request.getTopK()).isEqualTo(4);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.6);
    }

    @Test
    void honoursTopKOverrideFromRequest() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        chatService.ask(new ChatRequest("anything", 9));

        assertThat(capturedSearch().getTopK()).isEqualTo(9);
    }

    @Test
    void saysIDontKnowWithoutCallingTheModelWhenNothingIsRetrieved() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ChatAnswer answer = chatService.ask(new ChatRequest("What is the capital of Mars?", null));

        assertThat(answer.answer()).isEqualTo(ChatService.NO_CONTEXT_ANSWER);
        assertThat(answer.grounded()).isFalse();
        assertThat(answer.sources()).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void contextContainingTemplateCharactersIsPassedThroughVerbatim() {
        String code = "Map<String, Object> m = Map.of(\"k\", 1); if (x) { return; }";
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(chunk("chunk-9", "doc-9", "code.md", code, 0.8)));
        when(chatModel.call(any(Prompt.class))).thenReturn(response("It builds a map."));

        ChatAnswer answer = chatService.ask(new ChatRequest("What does the snippet do?", null));

        assertThat(answer.answer()).isEqualTo("It builds a map.");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getContents()).contains(code);
    }

    private SearchRequest capturedSearch() {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue();
    }

    private static Document chunk(String id, String documentId, String source, String text, double score) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(Map.of(DocumentMetadata.DOCUMENT_ID, documentId, DocumentMetadata.SOURCE, source))
                .score(score)
                .build();
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
