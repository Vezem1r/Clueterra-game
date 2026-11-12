package io.clueterra.questions.ai.model.review;

public record AiReviewDecisionResolution(
        AiReviewDecision decision,
        String proposedText,
        Integer proposedDifficulty,
        Double confidence,
        String policyReason) {
}
