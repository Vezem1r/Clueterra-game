package io.clueterra.questions.ai.model.embedding;

public record HintEmbeddingValue(
        Long hintId,
        float[] vector,
        String contentRevisionHash) {
}

