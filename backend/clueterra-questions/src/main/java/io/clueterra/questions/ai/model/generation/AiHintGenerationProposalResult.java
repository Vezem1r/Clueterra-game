package io.clueterra.questions.ai.model.generation;

import java.util.List;

public record AiHintGenerationProposalResult(
        Long countryId,
        String category,
        String hintType,
        String hintText,
        String imageSourceUrl,
        String imageKind,
        Integer difficulty,
        Double qualityScore,
        String factSummary,
        String evidenceSummary,
        String duplicateCheckSummary,
        List<String> searchTerms) {
}
