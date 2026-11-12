package io.clueterra.questions.ai.dto.overview;

public record AiEmbeddingOverviewResponse(
        boolean enabled,
        boolean available,
        String model,
        int dimensions,
        long activeHints,
        long embeddedHints,
        long missingHints,
        double coveragePercent,
        double duplicateThreshold,
        double relatedThreshold) {
}
