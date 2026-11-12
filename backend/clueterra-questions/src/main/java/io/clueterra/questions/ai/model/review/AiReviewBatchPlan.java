package io.clueterra.questions.ai.model.review;

import java.util.List;

public record AiReviewBatchPlan(
        int stageIndex,
        List<AiReviewCandidate> candidates,
        long estimatedInputTokens,
        int imageCount) {

    public int itemCount() {
        return candidates.size();
    }
}
