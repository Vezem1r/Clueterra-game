package io.clueterra.questions.ai.model.generation;

import java.util.List;

public record AiGenerationBatchPlan(
        int stageIndex,
        List<AiGenerationCountryGapCandidate> countries,
        long estimatedInputTokens) {

    public int itemCount() {
        return countries.size();
    }
}
