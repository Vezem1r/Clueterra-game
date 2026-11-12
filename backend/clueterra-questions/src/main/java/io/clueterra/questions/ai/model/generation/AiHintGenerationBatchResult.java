package io.clueterra.questions.ai.model.generation;

import java.util.List;

public record AiHintGenerationBatchResult(
        String batchSummary,
        List<AiHintGenerationProposalResult> proposals) {
}
