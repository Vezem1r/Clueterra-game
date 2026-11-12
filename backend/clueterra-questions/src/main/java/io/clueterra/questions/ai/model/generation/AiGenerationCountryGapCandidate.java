package io.clueterra.questions.ai.model.generation;

import java.time.Instant;

public record AiGenerationCountryGapCandidate(
        Long countryId,
        String countryCode,
        String countryName,
        int activeHintCount,
        int pendingProposalCount,
        int effectiveHintCount,
        int missingHintCount,
        Instant lastGeneratedAt) {
}
