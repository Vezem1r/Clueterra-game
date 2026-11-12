package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.config.generation.AiGenerationProperties;
import io.clueterra.questions.ai.config.generation.AiGenerationTokenizationProperties;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.generation.AiGenerationBatchPlan;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.service.generation.AiGenerationBatchPlanner;
import io.clueterra.questions.ai.service.support.AiTokenEstimator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiGenerationBatchPlannerTest {

    @Test
    void shouldRespectGenerationSoftCap() {
        AiGenerationBatchPlanner planner = getAiGenerationBatchPlanner();

        List<AiGenerationCountryGapCandidate> candidates = List.of(
                new AiGenerationCountryGapCandidate(1L, "HU", "Hungary", 10, 0, 10, 30, Instant.parse("2026-01-01T00:00:00Z")),
                new AiGenerationCountryGapCandidate(2L, "RO", "Romania", 10, 0, 10, 30, Instant.parse("2026-01-02T00:00:00Z")),
                new AiGenerationCountryGapCandidate(3L, "BG", "Bulgaria", 10, 0, 10, 30, Instant.parse("2026-01-03T00:00:00Z")),
                new AiGenerationCountryGapCandidate(4L, "PL", "Poland", 10, 0, 10, 30, Instant.parse("2026-01-04T00:00:00Z"))
        );

        List<AiGenerationBatchPlan> batches = planner.planBatches(candidates);

        assertEquals(2, batches.size());
        assertEquals(3, batches.get(0).itemCount());
        assertEquals(1, batches.get(1).itemCount());
    }

    @NotNull
    private static AiGenerationBatchPlanner getAiGenerationBatchPlanner() {
        AiProperties properties = new AiProperties();

        AiGenerationProperties generationProperties = new AiGenerationProperties();
        generationProperties.setMaxRequestsPerRun(10);
        generationProperties.setCountryBatchSizeSoftCap(3);
        AiGenerationTokenizationProperties tokenization = new AiGenerationTokenizationProperties();
        tokenization.setMaxInputTokensPerRequest(20_000);
        tokenization.setTargetInputUtilization(1.0);
        tokenization.setBaseRequestOverheadTokens(100);
        generationProperties.setTokenization(tokenization);
        properties.setGeneration(generationProperties);

        AiTokenEstimator estimator = new AiTokenEstimator(properties);
        return new AiGenerationBatchPlanner(properties, estimator);
    }
}
