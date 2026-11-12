package io.clueterra.questions.ai.dto.overview;

public record AiLaneOverviewResponse(
        String model,
        boolean schedulerEnabled,
        int maxRequestsPerRun,
        AiQuotaUsageResponse quota,
        AiReviewCoverageResponse reviewCoverage) {
}
