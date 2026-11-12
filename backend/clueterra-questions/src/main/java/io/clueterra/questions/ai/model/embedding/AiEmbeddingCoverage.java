package io.clueterra.questions.ai.model.embedding;

public record AiEmbeddingCoverage(
        long activeHints,
        long embeddedHints) {

    public long missingHints() {
        return Math.max(0, activeHints - embeddedHints);
    }

    public double coveragePercent() {
        return activeHints == 0 ? 0.0 : (100.0 * embeddedHints) / activeHints;
    }
}
