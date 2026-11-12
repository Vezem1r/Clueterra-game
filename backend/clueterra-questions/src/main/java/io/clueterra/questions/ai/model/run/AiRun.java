package io.clueterra.questions.ai.model.run;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;
import java.util.Map;

public record AiRun(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        AiRunType runType,
        AiRunTriggerType triggerType,
        AiRunStatus status,
        String provider,
        String model,
        String promptVersion,
        String requestedBy,
        int totalStages,
        int preparedItems,
        int processedItems,
        int changedItems,
        int generatedItems,
        long estimatedInputTokens,
        long estimatedOutputTokens,
        long actualInputTokens,
        long actualOutputTokens,
        int toolCallCount,
        Map<String, Object> configuration,
        Map<String, Object> summary,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
}
