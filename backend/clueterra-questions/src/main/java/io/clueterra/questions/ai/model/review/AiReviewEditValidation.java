package io.clueterra.questions.ai.model.review;

public record AiReviewEditValidation(
        boolean autoApplyEligible,
        Double semanticSimilarity,
        String summary) {
}
