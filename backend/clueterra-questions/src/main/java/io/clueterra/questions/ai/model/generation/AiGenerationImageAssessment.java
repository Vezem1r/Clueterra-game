package io.clueterra.questions.ai.model.generation;

import io.clueterra.questions.ai.service.generation.AiResolvedWikimediaImage;

public record AiGenerationImageAssessment(
        int proposalIndex,
        AiGeneratedImageKind imageKind,
        AiResolvedWikimediaImage resolvedImage,
        boolean validated,
        String answerTextVisibility,
        String answerSymbolVisibility,
        String visualDescription,
        String validationSummary) {
}
