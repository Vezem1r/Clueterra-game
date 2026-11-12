package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.model.review.AiReviewCoverage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewCoverageTest {

    @Test
    void calculatesCoverageWithinReviewableScope() {
        AiReviewCoverage coverage = new AiReviewCoverage(750, 1_000, 250, 30, 40);

        assertThat(coverage.coveragePercent()).isEqualTo(75.0);
    }

    @Test
    void treatsEmptyScopeAsComplete() {
        AiReviewCoverage coverage = new AiReviewCoverage(0, 0, 0, 0, 0);

        assertThat(coverage.coveragePercent()).isEqualTo(100.0);
    }
}
