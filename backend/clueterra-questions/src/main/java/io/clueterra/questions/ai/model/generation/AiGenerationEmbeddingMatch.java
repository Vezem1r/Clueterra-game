package io.clueterra.questions.ai.model.generation;

public record AiGenerationEmbeddingMatch(
        String sourceType,
        Long sourceId,
        Integer sourceProposalIndex,
        String hintText,
        double similarity) {
}
