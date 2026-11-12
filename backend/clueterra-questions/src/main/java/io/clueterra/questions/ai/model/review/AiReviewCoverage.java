package io.clueterra.questions.ai.model.review;

public record AiReviewCoverage(
        long processedHints,
        long totalHints,
        long pendingHints,
        long inReviewHints,
        long actionPendingHints) {

    public double coveragePercent() {
        return totalHints == 0 ? 100.0 : processedHints * 100.0 / totalHints;
    }
}
