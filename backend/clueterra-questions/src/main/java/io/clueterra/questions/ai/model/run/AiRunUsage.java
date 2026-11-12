package io.clueterra.questions.ai.model.run;

import java.time.Instant;
import java.util.Map;

public record AiRunUsage(
        Long id,
        Long runId,
        Long stageId,
        int requestIndex,
        String providerRequestId,
        String finishReason,
        long estimatedInputTokens,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        int toolCallCount,
        int imageInputCount,
        Long latencyMs,
        Map<String, Object> metadata,
        Instant createdAt) {
}
