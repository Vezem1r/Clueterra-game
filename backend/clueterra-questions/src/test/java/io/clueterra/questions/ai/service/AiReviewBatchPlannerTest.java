package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.review.AiReviewProperties;
import io.clueterra.questions.ai.config.review.AiReviewTokenizationProperties;
import io.clueterra.questions.ai.model.review.AiReviewBatchPlan;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.service.review.AiReviewBatchPlanner;
import io.clueterra.questions.ai.service.support.AiTokenEstimator;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiReviewBatchPlannerTest {

    @Test
    void shouldSplitBatchesBySoftCap() {
        AiReviewBatchPlanner planner = getAiReviewBatchPlanner();

        List<AiReviewCandidate> candidates = List.of(
                candidate(1L),
                candidate(2L),
                candidate(3L),
                candidate(4L),
                candidate(5L)
        );

        List<AiReviewBatchPlan> batches = planner.planBatches(candidates);

        assertEquals(3, batches.size());
        assertEquals(2, batches.get(0).itemCount());
        assertEquals(2, batches.get(1).itemCount());
        assertEquals(1, batches.get(2).itemCount());
    }

    @NotNull
    private static AiReviewBatchPlanner getAiReviewBatchPlanner() {
        AiProperties properties = new AiProperties();

        AiReviewProperties reviewProperties = new AiReviewProperties();
        reviewProperties.setMaxRequestsPerRun(10);
        reviewProperties.setDefaultBatchSizeSoftCap(2);
        AiReviewTokenizationProperties tokenization = new AiReviewTokenizationProperties();
        tokenization.setMaxInputTokensPerRequest(10_000);
        tokenization.setTargetInputUtilization(1.0);
        tokenization.setBaseRequestOverheadTokens(100);
        reviewProperties.setTokenization(tokenization);
        properties.setReview(reviewProperties);

        AiTokenEstimator estimator = new AiTokenEstimator(properties);
        return new AiReviewBatchPlanner(properties, estimator);
    }

    private AiReviewCandidate candidate(Long hintId) {
        return new AiReviewCandidate(
                hintId,
                10L,
                "HU",
                "Hungary",
                null,
                "A moderately sized hint text for planner tests " + hintId,
                null,
                null,
                30,
                BigDecimal.valueOf(82),
                DataSource.CIA_FACTBOOK,
                "CIA_TEST_" + hintId,
                2025,
                "rev-" + hintId,
                HintReviewStatus.NOT_REVIEWED,
                Instant.now(),
                null
        );
    }
}
