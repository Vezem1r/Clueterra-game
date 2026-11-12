package io.clueterra.questions.ai.model.review;

import java.util.List;

public record AiHintReviewBatchResult(
        String batchSummary,
        List<AiHintReviewItemResult> items) {
}
