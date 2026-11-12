package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.review.AiReviewProperties;
import io.clueterra.questions.ai.config.review.AiReviewTokenizationProperties;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.service.support.AiTokenEstimator;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTokenEstimatorTest {

    @Test
    void shouldAddImageTokenCostForImageReviewCandidate() {
        AiProperties properties = new AiProperties();
        AiReviewTokenizationProperties tokenization = new AiReviewTokenizationProperties();
        tokenization.setEstimatedImageTokens(900);
        AiReviewProperties reviewProperties = new AiReviewProperties();
        reviewProperties.setTokenization(tokenization);
        properties.setReview(reviewProperties);

        AiTokenEstimator estimator = new AiTokenEstimator(properties);

        AiReviewCandidate noImage = new AiReviewCandidate(
                1L, 10L, "HU", "Hungary", null, "Short hint text", null,
                null,
                25, BigDecimal.valueOf(80), DataSource.CIA_FACTBOOK, "CIA_POP", 2025,
                "rev-1", HintReviewStatus.NOT_REVIEWED, Instant.now(), null
        );
        AiReviewCandidate withImage = new AiReviewCandidate(
                2L, 10L, "HU", "Hungary", null, "Short hint text", "https://img.test/hint.jpg",
                null,
                25, BigDecimal.valueOf(80), DataSource.CIA_FACTBOOK, "CIA_POP", 2025,
                "rev-2", HintReviewStatus.NOT_REVIEWED, Instant.now(), null
        );

        int noImageTokens = estimator.estimateReviewCandidateTokens(noImage);
        int imageTokens = estimator.estimateReviewCandidateTokens(withImage);

        assertTrue(imageTokens >= noImageTokens + 900);
    }
}
