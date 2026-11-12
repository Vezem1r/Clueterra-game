package io.clueterra.questions.ai.controller.mapper;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.embedding.AiEmbeddingProperties;
import io.clueterra.questions.ai.config.generation.AiGenerationProperties;
import io.clueterra.questions.ai.config.review.AiReviewProperties;
import io.clueterra.questions.ai.dto.overview.AiEmbeddingOverviewResponse;
import io.clueterra.questions.ai.dto.overview.AiLaneOverviewResponse;
import io.clueterra.questions.ai.dto.overview.AiOverviewResponse;
import io.clueterra.questions.ai.dto.overview.AiQuotaUsageResponse;
import io.clueterra.questions.ai.dto.overview.AiReviewCoverageResponse;
import io.clueterra.questions.ai.dto.run.AiRunStartResponse;
import io.clueterra.questions.ai.model.embedding.AiEmbeddingCoverage;
import io.clueterra.questions.ai.model.quota.AiGenerationQuotaSnapshot;
import io.clueterra.questions.ai.model.quota.AiReviewQuotaSnapshot;
import io.clueterra.questions.ai.model.review.AiReviewCoverage;
import io.clueterra.questions.ai.model.run.AiRunPreparationResult;
import org.springframework.stereotype.Component;

@Component
public class AiAdminResponseMapper {

    public AiRunStartResponse toRunStartResponse(AiRunPreparationResult result) {
        return new AiRunStartResponse(
                result.runId(),
                result.runType().name(),
                result.status().name(),
                result.preparedStages(),
                result.preparedItems(),
                result.estimatedInputTokens(),
                result.message()
        );
    }

    public AiOverviewResponse toOverview(
            AiProperties aiProperties,
            AiReviewProperties reviewProperties,
            AiGenerationProperties generationProperties,
            AiReviewQuotaSnapshot reviewSnapshot,
            AiReviewCoverage reviewCoverage,
            AiGenerationQuotaSnapshot generationSnapshot,
            AiEmbeddingProperties embeddingProperties,
            AiEmbeddingCoverage embeddingCoverage,
            boolean embeddingAvailable) {
        return new AiOverviewResponse(
                aiProperties.getPromptVersion(),
                toLane(
                        aiProperties.reviewModelProfile().model(),
                        reviewProperties.isSchedulerEnabled(),
                        reviewProperties.getMaxRequestsPerRun(),
                        toReviewQuota(reviewSnapshot),
                        toReviewCoverage(reviewCoverage)
                ),
                toLane(
                        aiProperties.generationModelProfile().model(),
                        generationProperties.isSchedulerEnabled(),
                        generationProperties.getMaxRequestsPerRun(),
                        toGenerationQuota(generationSnapshot),
                        null
                ),
                toEmbedding(embeddingProperties, embeddingCoverage, embeddingAvailable)
        );
    }

    private AiEmbeddingOverviewResponse toEmbedding(
            AiEmbeddingProperties properties,
            AiEmbeddingCoverage coverage,
            boolean available) {
        return new AiEmbeddingOverviewResponse(
                properties.isEnabled(),
                available,
                properties.getModel(),
                properties.getOutputDimensionality(),
                coverage != null ? coverage.activeHints() : 0,
                coverage != null ? coverage.embeddedHints() : 0,
                coverage != null ? coverage.missingHints() : 0,
                coverage != null ? coverage.coveragePercent() : 0.0,
                properties.getDuplicateThreshold(),
                properties.getRelatedThreshold()
        );
    }

    private AiLaneOverviewResponse toLane(
            String model,
            boolean schedulerEnabled,
            int maxRequestsPerRun,
            AiQuotaUsageResponse quota,
            AiReviewCoverageResponse reviewCoverage) {
        return new AiLaneOverviewResponse(model, schedulerEnabled, maxRequestsPerRun, quota, reviewCoverage);
    }

    private AiReviewCoverageResponse toReviewCoverage(AiReviewCoverage coverage) {
        return new AiReviewCoverageResponse(
                coverage.processedHints(),
                coverage.totalHints(),
                coverage.pendingHints(),
                coverage.inReviewHints(),
                coverage.actionPendingHints(),
                coverage.coveragePercent()
        );
    }

    private AiQuotaUsageResponse toReviewQuota(AiReviewQuotaSnapshot s) {
        return new AiQuotaUsageResponse(
                s.requestsLastMinuteUsed(),
                s.requestsPerMinuteLimit(),
                s.requestsTodayUsed(),
                s.requestsPerDayLimit(),
                s.groundedPromptsTodayUsed(),
                s.groundedPromptsPerDayLimit(),
                s.inputTokensTodayUsed(),
                s.outputTokensTodayUsed(),
                s.failedRequestsToday(),
                s.nextDayResetAt()
        );
    }

    private AiQuotaUsageResponse toGenerationQuota(AiGenerationQuotaSnapshot s) {
        return new AiQuotaUsageResponse(
                s.requestsLastMinuteUsed(),
                s.requestsPerMinuteLimit(),
                s.requestsTodayUsed(),
                s.requestsPerDayLimit(),
                null,
                null,
                s.inputTokensTodayUsed(),
                s.outputTokensTodayUsed(),
                s.failedRequestsToday(),
                s.nextDayResetAt()
        );
    }
}
