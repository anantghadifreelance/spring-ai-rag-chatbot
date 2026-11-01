package com.anantghadi.ragchatbot.chat;

import com.anantghadi.ragchatbot.config.RagProperties;
import com.anantghadi.ragchatbot.document.DocumentMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Query pipeline: <em>retrieve top-k chunks &rarr; build grounded prompt &rarr; generate answer</em>.
 */
@Service
public class ChatService {

    /** Returned without calling the model when nothing relevant was retrieved. */
    public static final String NO_CONTEXT_ANSWER = "I don't know based on the provided documents.";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RagProperties.Retrieval retrieval;
    private final String systemPromptTemplate;

    public ChatService(ChatClient chatClient, VectorStore vectorStore, RagProperties properties,
            @Value("classpath:prompts/rag-system.st") Resource systemPrompt) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.retrieval = properties.retrieval();
        this.systemPromptTemplate = read(systemPrompt);
    }

    public ChatAnswer ask(ChatRequest request) {
        String question = request.question().strip();
        int topK = Objects.requireNonNullElse(request.topK(), retrieval.topK());

        List<Document> chunks = retrieve(question, topK);
        List<SourceReference> sources = chunks.stream().map(ChatService::toSource).toList();

        if (chunks.isEmpty()) {
            // Short-circuit: no grounding means no LLM call (cheaper and hallucination-proof).
            return new ChatAnswer(NO_CONTEXT_ANSWER, false, sources);
        }

        String answer = chatClient.prompt()
                .system(system -> system.text(systemPromptTemplate).param("context", formatContext(chunks)))
                .user(question)
                .call()
                .content();

        return new ChatAnswer(StringUtils.hasText(answer) ? answer.strip() : NO_CONTEXT_ANSWER, true, sources);
    }

    private List<Document> retrieve(String question, int topK) {
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(retrieval.similarityThreshold())
                .build());
        return results == null ? List.of() : results;
    }

    static String formatContext(List<Document> chunks) {
        return chunks.stream()
                .map(chunk -> "[source: %s | chunk: %s]%n%s".formatted(
                        metadata(chunk, DocumentMetadata.SOURCE), chunk.getId(), chunk.getText()))
                .collect(Collectors.joining("\n\n"));
    }

    private static SourceReference toSource(Document chunk) {
        return new SourceReference(metadata(chunk, DocumentMetadata.DOCUMENT_ID),
                metadata(chunk, DocumentMetadata.SOURCE), chunk.getId(), chunk.getScore());
    }

    private static String metadata(Document chunk, String key) {
        Object value = chunk.getMetadata().get(key);
        return value == null ? "unknown" : value.toString();
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Could not load system prompt " + resource, ex);
        }
    }
}
