package io.clueterra.questions.ai.model.generation;

import java.time.Instant;

public record AiGenerationImageInventory(
        int landmarkCount,
        int currencyCount,
        Instant lastRejectedLandmarkAt,
        Instant lastRejectedCurrencyAt) {
}
