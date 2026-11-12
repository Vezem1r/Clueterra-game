package io.clueterra.questions.ai.model.embedding;

public record PendingHintEmbedding(
        Long hintId,
        String hintText,
        String contentRevisionHash) {
}

