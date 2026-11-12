package io.clueterra.questions.ai.model.review;

import java.time.Instant;

public record AiHintReviewResult(
        Long id,
        Long runId,
        Long stageId,
        Long hintId,
        String sourceRevisionHash,
        AiReviewDecision decision,
        Double confidence,
        String summaryReason,
        String evidenceSummary,
        String proposedText,
        Integer proposedDifficulty,
        String proposedImageUrl,
        String disableReason,
        Long changeSetId,
        Instant createdAt,
        Integer retryAfterDays) {

}
