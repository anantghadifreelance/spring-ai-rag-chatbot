package com.anantghadi.ragchatbot.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic, offline {@link EmbeddingModel} for tests: a hashed bag-of-words vector, L2-normalised.
 * Texts sharing words get a high cosine similarity, which is enough to exercise real retrieval.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 512;
    private static final Pattern WORD = Pattern.compile("[a-z0-9]{3,}");

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(vectorize(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorize(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    static float[] vectorize(String text) {
        float[] vector = new float[DIMENSIONS];
        Matcher matcher = WORD.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            vector[Math.floorMod(matcher.group().hashCode(), DIMENSIONS)] += 1f;
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm > 0) {
            float scale = (float) (1 / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= scale;
            }
        }
        return vector;
    }
}
