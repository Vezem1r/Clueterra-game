package io.clueterra.questions.ai.dto.overview;

public record AiReviewCoverageResponse(
        long processedHints,
        long totalHints,
        long pendingHints,
        long inReviewHints,
        long actionPendingHints,
        double coveragePercent) {
}
