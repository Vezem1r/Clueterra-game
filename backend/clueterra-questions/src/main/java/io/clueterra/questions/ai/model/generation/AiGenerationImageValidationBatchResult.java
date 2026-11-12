package io.clueterra.questions.ai.model.generation;

import java.util.List;

public record AiGenerationImageValidationBatchResult(
        List<AiGenerationImageValidationItemResult> items) {
}
