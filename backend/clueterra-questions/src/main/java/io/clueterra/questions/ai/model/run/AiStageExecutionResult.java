package io.clueterra.questions.ai.model.run;

import java.util.Map;

public record AiStageExecutionResult(
        int processedItems,
        int changedItems,
        int generatedItems,
        long actualInputTokens,
        long actualOutputTokens,
        int toolCallCount,
        String providerRequestId,
        Map<String, Object> resultSummary) {
}
