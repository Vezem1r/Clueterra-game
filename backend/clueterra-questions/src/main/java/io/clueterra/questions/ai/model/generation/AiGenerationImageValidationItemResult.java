package io.clueterra.questions.ai.model.generation;

public record AiGenerationImageValidationItemResult(
        Integer proposalIndex,
        Boolean approved,
        String detectedKind,
        String answerTextVisibility,
        String answerSymbolVisibility,
        Boolean descriptionMatchesImage,
        String visualDescription,
        String validationSummary) {
}
