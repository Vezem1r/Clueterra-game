package io.clueterra.questions.ai.model.generation;

import java.util.List;

public record AiGenerationEmbeddingAssessment(
        int proposalIndex,
        Long countryId,
        String hintText,
        boolean checked,
        boolean conflict,
        double highestSimilarity,
        String validationSummary,
        List<AiGenerationEmbeddingMatch> matches) {
}
