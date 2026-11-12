package io.clueterra.questions.ai.dto.overview;

public record AiOverviewResponse(
        String promptVersion,
        AiLaneOverviewResponse review,
        AiLaneOverviewResponse generation,
        AiEmbeddingOverviewResponse embedding) {
}
